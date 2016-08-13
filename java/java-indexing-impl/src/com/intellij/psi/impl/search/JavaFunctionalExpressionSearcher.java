/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.search;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
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
    AtomicInteger exprCount = new AtomicInteger();
    AtomicInteger fileCount = new AtomicInteger();
    processOffsets(descriptors, (file, offsets) -> {
      fileCount.incrementAndGet();
      exprCount.addAndGet(offsets.size());
      return processFile(consumer, descriptors, file, offsets);
    });
    if (exprCount.get() > 0) {
      LOG.debug("Loaded " + exprCount.get() + " fun-expressions in " + fileCount.get() + " files");
    }
  }

  @TestOnly
  public static Set<VirtualFile> getFilesToSearchInPsi(PsiClass samClass) {
    Set<VirtualFile> result = new HashSet<>();
    processOffsets(calcDescriptors(new SearchParameters(samClass, samClass.getUseScope())), (file, offsets) -> result.add(file));
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
          descriptors.add(new SamDescriptor(samClass, saMethod, samType, convertToGlobalScope(project, scope)));
        }
      }
    });
    return descriptors;
  }

  @NotNull
  private static Set<VirtualFile> getLikelyFiles(List<SamDescriptor> descriptors) {
    return JBIterable.from(descriptors).flatMap(SamDescriptor::getMostLikelyFiles).toSet();
  }

  @NotNull
  private static MultiMap<VirtualFile, FunExprOccurrence> getAllOccurrences(List<SamDescriptor> descriptors) {
    MultiMap<VirtualFile, FunExprOccurrence> result = MultiMap.createLinkedSet();
    for (SamDescriptor descriptor : descriptors) {
      ReadAction.run(() -> {
        for (FunctionalExpressionKey key : descriptor.generateKeys()) {
          FileBasedIndex.getInstance().processValues(JavaFunctionalExpressionIndex.INDEX_ID, key, null, (file, infos) -> {
            ProgressManager.checkCanceled();
            result.putValues(file, infos);
            return true;
          }, new JavaSourceFilterScope(descriptor.useScope));
        }
      });
    }
    LOG.debug("Found " + result.values().size() + " fun-expressions in " + result.keySet().size() + " files");
    return result;
  }

  private static void processOffsets(List<SamDescriptor> descriptors, PairProcessor<VirtualFile, List<Integer>> processor) {
    if (descriptors.isEmpty()) return;

    List<PsiClass> samClasses = ContainerUtil.map(descriptors, d -> d.samClass);
    MultiMap<VirtualFile, FunExprOccurrence> allCandidates = getAllOccurrences(descriptors);
    for (VirtualFile vFile : putLikelyFilesFirst(descriptors, allCandidates.keySet())) {
      List<FunExprOccurrence> toLoad = filterInapplicable(samClasses, vFile, allCandidates.get(vFile));
      if (!toLoad.isEmpty()) {
        LOG.trace("To load " + vFile.getPath() + " with values: " + toLoad);
        if (!processor.process(vFile, ContainerUtil.map(toLoad, it -> it.funExprOffset))) {
          return;
        }
      }
    }
  }

  @NotNull
  private static Set<VirtualFile> putLikelyFilesFirst(List<SamDescriptor> descriptors, Set<VirtualFile> allFiles) {
    Set<VirtualFile> orderedFiles = new LinkedHashSet<>(allFiles);
    orderedFiles.retainAll(getLikelyFiles(descriptors));
    orderedFiles.addAll(allFiles);
    return orderedFiles;
  }

  @NotNull
  private static List<FunExprOccurrence> filterInapplicable(List<PsiClass> samClasses,
                                                            VirtualFile vFile,
                                                            Collection<FunExprOccurrence> occurrences) {
    return ReadAction.compute(() -> ContainerUtil.filter(occurrences, it -> it.canHaveType(samClasses, vFile)));
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

  @NotNull
  private static GlobalSearchScope convertToGlobalScope(Project project, SearchScope useScope) {
    final GlobalSearchScope scope;
    if (useScope instanceof GlobalSearchScope) {
      scope = (GlobalSearchScope)useScope;
    }
    else if (useScope instanceof LocalSearchScope) {
      final Set<VirtualFile> files = new HashSet<>();
      ContainerUtil.addAllNotNull(files, ContainerUtil.map(((LocalSearchScope)useScope).getScope(), PsiUtilCore::getVirtualFile));
      scope = GlobalSearchScope.filesScope(project, files);
    }
    else {
      scope = new EverythingGlobalScope(project);
    }
    return scope;
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
    final GlobalSearchScope useScope;
    final int samParamCount;
    final boolean booleanCompatible;
    final boolean isVoid;

    SamDescriptor(PsiClass samClass, PsiMethod samMethod, PsiType samType, GlobalSearchScope useScope) {
      this.samClass = samClass;
      this.useScope = useScope;
      this.samParamCount = samMethod.getParameterList().getParametersCount();
      this.booleanCompatible = FunctionalExpressionKey.isBooleanCompatible(samType);
      this.isVoid = PsiType.VOID.equals(samType);
    }

    List<FunctionalExpressionKey> generateKeys() {
      List<FunctionalExpressionKey> result = new ArrayList<>();
      for (String lambdaType : new String[]{assertNotNull(samClass.getName()), ""}) {
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
    private Set<VirtualFile> getMostLikelyFiles() {
      Set<VirtualFile> files = ContainerUtil.newLinkedHashSet();
      ReadAction.run(() -> {
        if (!samClass.isValid()) return;

        String className = samClass.getName();
        Project project = samClass.getProject();
        if (className == null) return;

        Set<String> likelyNames = ContainerUtil.newLinkedHashSet(className);
        StubIndex.getInstance().processElements(JavaMethodParameterTypesIndex.getInstance().getKey(), className,
                                                project, useScope, PsiMethod.class, method -> {
            ProgressManager.checkCanceled();
            likelyNames.add(method.getName());
            return true;
          });

        PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
        Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
        for (String word : likelyNames) {
          helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, word, processor);
        }
      });
      return files;
    }

  }

}