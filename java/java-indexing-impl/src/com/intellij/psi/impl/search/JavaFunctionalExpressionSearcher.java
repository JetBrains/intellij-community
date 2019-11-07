// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
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
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, SearchParameters> {
  private static final Logger LOG = Logger.getInstance(JavaFunctionalExpressionSearcher.class);
  public static final int SMART_SEARCH_THRESHOLD = 5;

  @Override
  public void processQuery(@NotNull SearchParameters p, @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    List<SamDescriptor> descriptors = calcDescriptors(p);
    Project project = PsiUtilCore.getProjectInReadAction(p.getElementToSearch());
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
      processOffsets(descriptors, project, (file, occurrences) -> {
        fileCount.incrementAndGet();
        exprCount.addAndGet(occurrences.size());
        return processFile(consumer, descriptors, file, occurrences);
      });
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }
    if (exprCount.get() > 0 && LOG.isDebugEnabled()) {
      LOG.debug("Loaded " + exprCount.get() + " fun-expressions in " + fileCount.get() + " files");
    }
  }

  @TestOnly
  public static Set<VirtualFile> getFilesToSearchInPsi(@NotNull PsiClass samClass) {
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

      Set<PsiClass> visited = new HashSet<>();
      processSubInterfaces(aClass, visited);
      for (PsiClass samClass : visited) {
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
  private static Set<VirtualFile> getLikelyFiles(@NotNull List<? extends SamDescriptor> descriptors,
                                                 @NotNull Collection<? extends VirtualFile> candidateFiles,
                                                 @NotNull Project project) {
    final GlobalSearchScope candidateFilesScope = GlobalSearchScope.filesScope(project, candidateFiles);
    return JBIterable.from(descriptors).flatMap(descriptor -> ((SamDescriptor)descriptor).getMostLikelyFiles(candidateFilesScope)).toSet();
  }

  @NotNull
  private static MultiMap<VirtualFile, FunExprOccurrence> getAllOccurrences(@NotNull List<? extends SamDescriptor> descriptors) {
    MultiMap<VirtualFile, FunExprOccurrence> result = MultiMap.createLinkedSet();
    descriptors.get(0).dumbService.runReadActionInSmartMode(() -> processIndexValues(descriptors, null, (file, infos) -> {
      result.putValues(file, infos.values());
      return true;
    }));
    LOG.debug("Found " + result.values().size() + " fun-expressions in " + result.keySet().size() + " files");
    return result;
  }

  private static void processIndexValues(@NotNull List<? extends SamDescriptor> descriptors,
                                         @Nullable VirtualFile inFile,
                                         @NotNull FileBasedIndex.ValueProcessor<? super Map<Integer, FunExprOccurrence>> processor) {
    for (SamDescriptor descriptor : descriptors) {
      GlobalSearchScope scope = new JavaSourceFilterScope(descriptor.effectiveUseScope);
      for (FunctionalExpressionKey key : descriptor.keys) {
        FileBasedIndex.getInstance().processValues(JavaFunctionalExpressionIndex.INDEX_ID, key, inFile, processor, scope);
      }
    }
  }

  private static void processOffsets(@NotNull List<? extends SamDescriptor> descriptors,
                                     @NotNull Project project,
                                     @NotNull PairProcessor<? super VirtualFile, ? super Set<FunExprOccurrence>> processor) {
    if (descriptors.isEmpty()) return;

    List<PsiClass> samClasses = ContainerUtil.map(descriptors, d -> d.samClass);
    MultiMap<VirtualFile, FunExprOccurrence> allCandidates = getAllOccurrences(descriptors);
    if (allCandidates.isEmpty()) return;

    Set<VirtualFile> allFiles = allCandidates.keySet();
    Set<VirtualFile> filesFirst = getLikelyFiles(descriptors, allFiles, project);
    Processor<VirtualFile> vFileProcessor = vFile -> {
      Set<FunExprOccurrence> toLoad = filterInapplicable(samClasses, vFile, allCandidates.get(vFile), project);
      if (!toLoad.isEmpty()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("To load " + vFile.getPath() + " with values: " + toLoad);
        }
        return processor.process(vFile, toLoad);
      }
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(filesFirst),
                     ProgressIndicatorProvider.getGlobalProgressIndicator(), vFileProcessor)) return;
    allFiles.removeAll(filesFirst);
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(allFiles),
                     ProgressIndicatorProvider.getGlobalProgressIndicator(), vFileProcessor);
  }

  @NotNull
  private static Set<FunExprOccurrence> filterInapplicable(@NotNull List<? extends PsiClass> samClasses,
                                                           @NotNull VirtualFile vFile,
                                                           @NotNull Collection<? extends FunExprOccurrence> occurrences,
                                                           @NotNull Project project) {
    return DumbService.getInstance(project).runReadActionInSmartMode(
      () -> new HashSet<>(ContainerUtil.filter(occurrences, it -> it.canHaveType(samClasses, vFile))));
  }

  private static boolean processFile(@NotNull Processor<? super PsiFunctionalExpression> consumer,
                                     @NotNull List<? extends SamDescriptor> descriptors,
                                     @NotNull VirtualFile vFile,
                                     @NotNull Set<? extends FunExprOccurrence> occurrences) {
    return descriptors.get(0).dumbService.runReadActionInSmartMode(() -> {
      PsiFile file = descriptors.get(0).samClass.getManager().findFile(vFile);
      if (!(file instanceof PsiJavaFile)) {
        LOG.error("Non-java file " + file + "; " + vFile);
        return true;
      }

      TIntArrayList offsets = getOccurrenceOffsets(descriptors, vFile, occurrences);

      for (int i = 0; i < offsets.size(); i++) {
        int offset = offsets.get(i);
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

  @NotNull
  private static TIntArrayList getOccurrenceOffsets(@NotNull List<? extends SamDescriptor> descriptors,
                                                    @NotNull VirtualFile vFile,
                                                    @NotNull Set<? extends FunExprOccurrence> occurrences) {
    TIntArrayList offsets = new TIntArrayList();
    processIndexValues(descriptors, vFile, (__, infos) -> {
      for (Map.Entry<Integer, FunExprOccurrence> entry : infos.entrySet()) {
        if (occurrences.contains(entry.getValue())) {
          offsets.add(entry.getKey());
        }
      }
      return true;
    });
    return offsets;
  }

  private static boolean hasType(@NotNull List<? extends SamDescriptor> descriptors, @NotNull PsiFunctionalExpression expression) {
    if (!canHaveType(expression, ContainerUtil.map(descriptors, d -> d.samClass))) return false;

    PsiClass actualClass = LambdaUtil.resolveFunctionalInterfaceClass(expression);
    return ContainerUtil.exists(descriptors, d -> InheritanceUtil.isInheritorOrSelf(actualClass, d.samClass, true));
  }

  private static boolean canHaveType(@NotNull PsiFunctionalExpression expression, @NotNull List<? extends PsiClass> samClasses) {
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

  private static boolean hasJava8Modules(@NotNull Project project) {
    final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
      if (extension != null) {
        final LanguageLevel level = extension.getLanguageLevel();
        if (level == null ? projectLevelIsHigh : level.isAtLeast(LanguageLevel.JDK_1_8)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void processSubInterfaces(@NotNull PsiClass base, @NotNull Set<? super PsiClass> visited) {
    if (!visited.add(base)) return;

    DirectClassInheritorsSearch.search(base).forEach(candidate -> {
      if (candidate.isInterface()) {
        processSubInterfaces(candidate, visited);
      }
      return true;
    });
  }

  private static class SamDescriptor {
    final PsiClass samClass;
    final int samParamCount;
    final boolean booleanCompatible;
    final boolean isVoid;
    final DumbService dumbService;
    final List<FunctionalExpressionKey> keys;
    GlobalSearchScope effectiveUseScope;

    SamDescriptor(@NotNull PsiClass samClass, @NotNull PsiMethod samMethod, @NotNull PsiType samType, @NotNull GlobalSearchScope useScope) {
      this.samClass = samClass;
      effectiveUseScope = useScope;
      samParamCount = samMethod.getParameterList().getParametersCount();
      booleanCompatible = FunctionalExpressionKey.isBooleanCompatible(samType);
      isVoid = PsiType.VOID.equals(samType);
      dumbService = DumbService.getInstance(samClass.getProject());
      keys = generateKeys();
    }

    @NotNull
    private List<FunctionalExpressionKey> generateKeys() {
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
    private Set<VirtualFile> getMostLikelyFiles(@NotNull GlobalSearchScope searchScope) {
      Set<VirtualFile> files = new LinkedHashSet<>();
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

        PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.getInstance(project);
        Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
        for (String word : likelyNames) {
          helper.processFilesWithText(searchScope, UsageSearchContext.IN_CODE, true, word, processor);
        }
      });
      return files;
    }
  }

  private static boolean performSearchUsingCompilerIndices(@NotNull List<? extends SamDescriptor> descriptors,
                                                           @NotNull GlobalSearchScope searchScope,
                                                           @NotNull Project project,
                                                           @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstance(project);
    if (compilerReferenceService == null) return true;
    for (SamDescriptor descriptor : descriptors) {
      CompilerDirectHierarchyInfo info = compilerReferenceService.getFunExpressions(descriptor.samClass, searchScope, JavaFileType.INSTANCE);
      if (info != null && !processFunctionalExpressions(info, descriptor, consumer)) {
        return false;
      }
    }
    return true;
  }


  private static boolean processFunctionalExpressions(@NotNull CompilerDirectHierarchyInfo funExprInfo,
                                                      @NotNull SamDescriptor descriptor,
                                                      @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    if (!ContainerUtil.process(funExprInfo.getHierarchyChildren().iterator(), fe -> consumer.process((PsiFunctionalExpression)fe))) {
      return false;
    }
    GlobalSearchScope dirtyScope = funExprInfo.getDirtyScope();
    descriptor.effectiveUseScope = descriptor.effectiveUseScope.intersectWith(dirtyScope);
    return true;
  }

}