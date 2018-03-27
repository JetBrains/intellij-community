/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.FunExprOccurrence;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch.SearchParameters;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher");
  public static final int SMART_SEARCH_THRESHOLD = 5;

  @Override
  public void processQuery(@NotNull SearchParameters p, @NotNull Processor<PsiFunctionalExpression> consumer) {
    List<SamDescriptor> descriptors = calcDescriptors(p);
    Project project = PsiUtilCore.getProjectInReadAction(p.getElementToSearch());
    if (project == null) return;

    SearchScope searchScope = ReadAction.compute(() -> p.getEffectiveSearchScope());
    if (searchScope instanceof GlobalSearchScope && !performSearchUsingCompilerIndices(descriptors,
                                                                                       (GlobalSearchScope)searchScope,
                                                                                       project,
                                                                                       consumer)) {
      return;
    }

    AtomicInteger exprCount = new AtomicInteger();
    AtomicInteger fileCount = new AtomicInteger();

    PsiManager manager = ReadAction.compute(() -> p.getElementToSearch().getManager());
    manager.startBatchFilesProcessingMode();
    try {
      processOffsets(descriptors, project, (file, offsets) -> {
        fileCount.incrementAndGet();
        exprCount.addAndGet(offsets.size());
        return processFile(consumer, descriptors, file, offsets);
      });
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }
    if (exprCount.get() > 0) {
      LOG.debug("Loaded " + exprCount.get() + " fun-expressions in " + fileCount.get() + " files");
    }
  }

  @TestOnly
  public static Set<VirtualFile> getFilesToSearchInPsi(PsiClass samClass) {
    Set<VirtualFile> result = new HashSet<>();
    processOffsets(calcDescriptors(new SearchParameters(samClass, samClass.getUseScope())), samClass.getProject(), (file, offsets) -> result.add(file));
    return result;
  }

  @NotNull
  private static List<SamDescriptor> calcDescriptors(@NotNull SearchParameters queryParameters) {
    List<SamDescriptor> descriptors = new ArrayList<>();

    ReadAction.run(() -> {
      PsiClass aClass = queryParameters.getElementToSearch();
      if (!aClass.isValid() || !aClass.isInterface()) {
        return;
      }
      Project project = aClass.getProject();
      if (InjectedLanguageManager.getInstance(project).isInjectedFragment(aClass.getContainingFile()) || !hasJava8Modules(project)) {
        return;
      }

      for (PsiClass samClass : processSubInterfaces(aClass)) {
        if (LambdaUtil.isFunctionalClass(samClass)) {
          PsiMethod saMethod = assertNotNull(LambdaUtil.getFunctionalInterfaceMethod(samClass));
          PsiType samType = saMethod.getReturnType();
          if (samType == null) continue;

          SearchScope scope = samClass.getUseScope().intersectWith(queryParameters.getEffectiveSearchScope());
          descriptors.add(new SamDescriptor(samClass, saMethod, samType, GlobalSearchScopeUtil.toGlobalSearchScope(scope, project)));
        }
      }
    });
    return descriptors;
  }

  @NotNull
  private static Set<VirtualFile> getLikelyFiles(List<SamDescriptor> descriptors, Collection<VirtualFile> candidateFiles, Project project) {
    final GlobalSearchScope candidateFilesScope = GlobalSearchScope.filesScope(project, candidateFiles);
    return JBIterable.from(descriptors).flatMap(descriptor -> descriptor.getMostLikelyFiles(candidateFilesScope)).toSet();
  }

  @NotNull
  private static MultiMap<VirtualFile, FunExprOccurrence> getAllOccurrences(List<SamDescriptor> descriptors) {
    MultiMap<VirtualFile, FunExprOccurrence> result = MultiMap.createLinkedSet();
    for (SamDescriptor descriptor : descriptors) {
      descriptor.dumbService.runReadActionInSmartMode(() -> {
        for (FunctionalExpressionKey key : descriptor.generateKeys()) {
          FileBasedIndex.getInstance().processValues(JavaFunctionalExpressionIndex.INDEX_ID, key, null, (file, infos) -> {
            ProgressManager.checkCanceled();
            result.putValues(file, infos);
            return true;
          }, new JavaSourceFilterScope(descriptor.effectiveUseScope));
        }
      });
    }
    LOG.debug("Found " + result.values().size() + " fun-expressions in " + result.keySet().size() + " files");
    return result;
  }

  private static void processOffsets(List<SamDescriptor> descriptors, Project project, PairProcessor<VirtualFile, List<Integer>> processor) {
    if (descriptors.isEmpty()) return;

    List<PsiClass> samClasses = ContainerUtil.map(descriptors, d -> d.samClass);
    MultiMap<VirtualFile, FunExprOccurrence> allCandidates = getAllOccurrences(descriptors);
    if (allCandidates.isEmpty()) return;

    for (VirtualFile vFile : putLikelyFilesFirst(descriptors, allCandidates.keySet(), project)) {
      List<FunExprOccurrence> toLoad = filterInapplicable(samClasses, vFile, allCandidates.get(vFile), project);
      if (!toLoad.isEmpty()) {
        LOG.trace("To load " + vFile.getPath() + " with values: " + toLoad);
        if (!processor.process(vFile, ContainerUtil.map(toLoad, it -> it.funExprOffset))) {
          return;
        }
      }
    }
  }

  @NotNull
  private static Set<VirtualFile> putLikelyFilesFirst(List<SamDescriptor> descriptors, Set<VirtualFile> allFiles, Project project) {
    Set<VirtualFile> orderedFiles = new LinkedHashSet<>(allFiles.size());
    orderedFiles.addAll(getLikelyFiles(descriptors, allFiles, project));
    orderedFiles.addAll(allFiles);
    return orderedFiles;
  }

  @NotNull
  private static List<FunExprOccurrence> filterInapplicable(List<PsiClass> samClasses,
                                                            VirtualFile vFile,
                                                            Collection<FunExprOccurrence> occurrences, Project project) {
    return DumbService.getInstance(project).runReadActionInSmartMode(
      () -> project.isDisposed() ? Collections.emptyList()
                                 : ContainerUtil.filter(occurrences, it -> it.canHaveType(samClasses, vFile)));
  }

  private static boolean processFile(@NotNull Processor<PsiFunctionalExpression> consumer,
                                     List<SamDescriptor> descriptors,
                                     VirtualFile vFile, Collection<Integer> offsets) {
    return ReadAction.compute(() -> {
      PsiFile file = descriptors.get(0).samClass.getManager().findFile(vFile);
      if (!(file instanceof PsiJavaFile)) {
        LOG.error("Non-java file " + file + "; " + vFile);
        return true;
      }

      for (Integer offset : offsets) {
        PsiFunctionalExpression expression = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiFunctionalExpression.class, false);
        if (expression == null || expression.getTextRange().getStartOffset() != offset) {
          LOG.error("Fun expression not found in " + file + " at " + offset);
          continue;
        }

        if (hasType(descriptors, expression) && !consumer.process(expression)) {
          return false;
        }
      }

      return true;
    });
  }

  private static boolean hasType(List<SamDescriptor> descriptors, PsiFunctionalExpression expression) {
    if (!canHaveType(expression, ContainerUtil.map(descriptors, d -> d.samClass))) return false;

    PsiClass actualClass = PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType());
    return ContainerUtil.exists(descriptors, d -> InheritanceUtil.isInheritorOrSelf(actualClass, d.samClass, true));
  }

  private static boolean canHaveType(PsiFunctionalExpression expression, List<PsiClass> samClasses) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
      PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
      int argIndex = Arrays.asList(args).indexOf(expression);
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)parent.getParent()).getMethodExpression();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      String methodName = methodExpression.getReferenceName();
      if (qualifier != null && methodName != null && argIndex >= 0) {
        Set<PsiClass> approximateTypes = ApproximateResolver.getPossibleTypes(qualifier, 10);
        List<PsiMethod> methods = approximateTypes == null ? null :
                                  ApproximateResolver.getPossibleMethods(approximateTypes, methodName, args.length);
        return methods == null ||
               ContainerUtil.exists(methods, m -> FunExprOccurrence.hasCompatibleParameter(m, argIndex, samClasses));
      }
    }
    return true;
  }

  private static boolean hasJava8Modules(Project project) {
    final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
      if (extension != null) {
        final LanguageLevel level = extension.getLanguageLevel();
        if (level == null && projectLevelIsHigh || level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<PsiClass> processSubInterfaces(PsiClass base) {
    Set<PsiClass> result = new HashSet<>();
    new Object() {
      void visit(PsiClass c) {
        if (!result.add(c)) return;

        DirectClassInheritorsSearch.search(c).forEach(candidate -> {
          if (candidate.isInterface()) {
            visit(candidate);
          }
          return true;
        });
      }
    }.visit(base);
    return result;
  }

  private static class SamDescriptor {
    final PsiClass samClass;
    final int samParamCount;
    final boolean booleanCompatible;
    final boolean isVoid;
    final DumbService dumbService;
    GlobalSearchScope effectiveUseScope;

    SamDescriptor(PsiClass samClass, PsiMethod samMethod, PsiType samType, GlobalSearchScope useScope) {
      this.samClass = samClass;
      this.effectiveUseScope = useScope;
      this.samParamCount = samMethod.getParameterList().getParametersCount();
      this.booleanCompatible = FunctionalExpressionKey.isBooleanCompatible(samType);
      this.isVoid = PsiType.VOID.equals(samType);
      this.dumbService = DumbService.getInstance(samClass.getProject());
    }

    List<FunctionalExpressionKey> generateKeys() {
      String name = samClass.isValid() ? samClass.getName() : null;
      if (name == null) return Collections.emptyList();

      List<FunctionalExpressionKey> result = new ArrayList<>();
      for (String lambdaType : new String[]{assertNotNull(name), ""}) {
        for (int lambdaParamCount : new int[]{FunctionalExpressionKey.UNKNOWN_PARAM_COUNT, samParamCount}) {
          result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.UNKNOWN, lambdaType));
          if (isVoid) {
            result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.VOID, lambdaType));
          } else {
            if (booleanCompatible) {
              result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.BOOLEAN, lambdaType));
            }
            result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.NON_VOID, lambdaType));
          }
        }
      }

      return result;
    }

    @NotNull
    private Set<VirtualFile> getMostLikelyFiles(GlobalSearchScope searchScope) {
      Set<VirtualFile> files = ContainerUtil.newLinkedHashSet();
      dumbService.runReadActionInSmartMode(() -> {
        if (!samClass.isValid()) return;

        String className = samClass.getName();
        Project project = samClass.getProject();
        if (className == null) return;

        Set<String> likelyNames = ContainerUtil.newLinkedHashSet(className);
        StubIndex.getInstance().processElements(JavaMethodParameterTypesIndex.getInstance().getKey(), className,
                                                project, effectiveUseScope, PsiMethod.class, method -> {
            ProgressManager.checkCanceled();
            likelyNames.add(method.getName());
            return true;
          });

        PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
        Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
        for (String word : likelyNames) {
          helper.processFilesWithText(searchScope, UsageSearchContext.IN_CODE, true, word, processor);
        }
      });
      return files;
    }
  }

  private static boolean performSearchUsingCompilerIndices(@NotNull List<SamDescriptor> descriptors,
                                                           @NotNull GlobalSearchScope searchScope,
                                                           @NotNull Project project,
                                                           @NotNull Processor<PsiFunctionalExpression> consumer) {
    CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstance(project);
    if (compilerReferenceService == null) return true;
    for (SamDescriptor descriptor : descriptors) {
      if (!processFunctionalExpressions(performSearchUsingCompilerIndices(descriptor,
                                                                          searchScope,
                                                                          compilerReferenceService), descriptor, consumer)) {
        return false;
      }
    }
    return true;
  }

  private static CompilerDirectHierarchyInfo performSearchUsingCompilerIndices(@NotNull SamDescriptor descriptor,
                                                                               @NotNull GlobalSearchScope searchScope,
                                                                               @NotNull CompilerReferenceService service) {
    return service.getFunExpressions(descriptor.samClass, searchScope, JavaFileType.INSTANCE);
  }


  private static boolean processFunctionalExpressions(@Nullable CompilerDirectHierarchyInfo funExprInfo,
                                                      @NotNull SamDescriptor descriptor,
                                                      @NotNull Processor<PsiFunctionalExpression> consumer) {
    if (funExprInfo != null) {
      if (!ContainerUtil.process(funExprInfo.getHierarchyChildren().iterator(), fe -> consumer.process((PsiFunctionalExpression)fe))) return false;
      GlobalSearchScope dirtyScope = funExprInfo.getDirtyScope();
      descriptor.effectiveUseScope = descriptor.effectiveUseScope.intersectWith(dirtyScope);
    }
    return true;
  }

}