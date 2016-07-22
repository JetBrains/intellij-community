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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.java.stubs.JavaMethodElementType;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.IntStream;

public class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#" + JavaFunctionalExpressionSearcher.class.getName());
  /**
   * The least number of candidate files with functional expressions that directly scanning them becomes expensive
   * and more advanced ways of searching become necessary: e.g. first searching for methods where the functional interface class is used
   * and then for their usages,
    */
  public static final int SMART_SEARCH_THRESHOLD = 5;

  @Override
  public void processQuery(@NotNull FunctionalExpressionSearch.SearchParameters queryParameters,
                           @NotNull Processor<PsiFunctionalExpression> consumer) {
    final GlobalSearchScope useScope;
    final PsiClass aClass;
    final Project project;
    final int expectedFunExprParamsCount;
    final boolean isVoid;

    AccessToken token = ReadAction.start();
    try {
      aClass = queryParameters.getElementToSearch();
      if (!aClass.isValid() || !LambdaUtil.isFunctionalClass(aClass)) return;

      project = aClass.getProject();
      final Set<Module> highLevelModules = getJava8Modules(project);
      if (highLevelModules.isEmpty()) return;

      useScope = convertToGlobalScope(project, queryParameters.getEffectiveSearchScope());

      final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
      LOG.assertTrue(functionalInterfaceMethod != null);
      expectedFunExprParamsCount = functionalInterfaceMethod.getParameterList().getParameters().length;
      isVoid = PsiType.VOID.equals(functionalInterfaceMethod.getReturnType());
    } finally {
      token.finish();
    }

    //collect all files with '::' and '->' in useScope
    Set<VirtualFile> candidateFiles = getFilesWithFunctionalExpressionsScope(project, new JavaSourceFilterScope(useScope));
    if (candidateFiles.isEmpty()) {
      return;
    }

    MultiMap<FunctionalExpressionKey, GlobalSearchScope> queries =
      collectQueryKeys(useScope, aClass, project, expectedFunExprParamsCount, isVoid, candidateFiles);

    for (PsiFunctionalExpression expression : getCandidates(useScope, project, queries)) {
      if (!ReadAction.compute(() -> {
        if (expression.isValid() &&
            InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType()), aClass, true)) {
          if (!consumer.process(expression)) {
            return false;
          }
        }

        return true;
      })) {
        return;
      }
    }
  }

  @NotNull
  private static Collection<? extends PsiFunctionalExpression> getCandidates(GlobalSearchScope useScope,
                                                                             Project project,
                                                                             MultiMap<FunctionalExpressionKey, GlobalSearchScope> queries) {
    MultiMap<PsiFile, PsiFunctionalExpression> exprs = MultiMap.createLinked();
    for (Map.Entry<FunctionalExpressionKey, Collection<GlobalSearchScope>> entry : queries.entrySet()) {
      ReadAction.run(() -> {
        ProgressManager.checkCanceled();
        GlobalSearchScope combinedScope = useScope.intersectWith(
          GlobalSearchScope.union(entry.getValue().toArray(new GlobalSearchScope[0])));
        StubIndex.getInstance().processElements(JavaStubIndexKeys.FUNCTIONAL_EXPRESSIONS,
                                                entry.getKey(),
                                                project, combinedScope, null,
                                                PsiFunctionalExpression.class,
                                                expression -> {
                                                  exprs.putValue(expression.getContainingFile(), expression);
                                                  return true;
                                                });
      });
    }

    Collection<? extends PsiFunctionalExpression> result = exprs.values();
    LOG.info("checking " + result.size() + " fun-expressions in " + exprs.size() + " files");
    return result;
  }

  @NotNull
  private static MultiMap<FunctionalExpressionKey, GlobalSearchScope> collectQueryKeys(GlobalSearchScope useScope,
                                                                                       PsiClass aClass,
                                                                                       Project project,
                                                                                       int samParamCount,
                                                                                       boolean samVoid,
                                                                                       Set<VirtualFile> candidateFiles) {
    //collect all methods with parameter of functional interface or free type parameter type
    Collection<PsiMethod> methodCandidates = getCandidateMethodsWithSuitableParams(aClass, project, useScope, candidateFiles, samParamCount, samVoid);

    MultiMap<FunctionalExpressionKey, GlobalSearchScope> queries = MultiMap.createSet();
    for (FunctionalExpressionKey key : generateKeys(samParamCount, samVoid, "", -1, -1)) {
      queries.putValue(key, useScope); // check all fun-exprs that aren't inside calls
    }

    //find all usages of method candidates in files with functional expressions
    for (final PsiMethod psiMethod : methodCandidates) {
      ReadAction.run(() -> {
        if (!psiMethod.isValid()) return;

        final GlobalSearchScope methodUseScope = convertToGlobalScope(project, psiMethod.getUseScope());
        for (FunctionalExpressionKey key : getQueryKeys(aClass, samParamCount, samVoid, psiMethod)) {
          queries.putValue(key, methodUseScope);
        }
      });
    }
    return queries;
  }

  @NotNull
  private static Set<FunctionalExpressionKey> getQueryKeys(PsiClass samClass,
                                                           int samParamCount,
                                                           boolean samVoid,
                                                           PsiMethod calledMethod) {
    Set<FunctionalExpressionKey> keys = new HashSet<>();

    String methodName = calledMethod.getName();
    PsiParameter[] parameters = calledMethod.getParameterList().getParameters();
    for (int paramIndex = 0; paramIndex < parameters.length; paramIndex++) {
      PsiParameter parameter = parameters[paramIndex];
      if (canPassFunctionalExpression(samClass, parameter)) {
        for (int argCount : getPossibleArgCounts(parameters, paramIndex)) {
          for (int argIndex : getPossibleArgIndices(parameter, paramIndex, argCount)) {
            keys.addAll(generateKeys(samParamCount, samVoid, methodName, argCount, argIndex));
          }
        }
      }
    }

    return keys;
  }

  private static List<FunctionalExpressionKey> generateKeys(int samMethodParamsCount,
                                                            boolean samMethodVoid,
                                                            String methodName, int argCount, int argIndex) {
    List<FunctionalExpressionKey> result = new ArrayList<>();
    for (int lambdaParamCount : new int[]{FunctionalExpressionKey.UNKNOWN_PARAM_COUNT, samMethodParamsCount}) {
      result.add(new FunctionalExpressionKey(methodName, lambdaParamCount, argCount, argIndex, ThreeState.UNSURE));
      result.add(new FunctionalExpressionKey(methodName, lambdaParamCount, argCount, argIndex, ThreeState.fromBoolean(samMethodVoid)));
    }
    return result;
  }

  private static int[] getPossibleArgCounts(PsiParameter[] parameters, int paramIndex) {
    if (parameters[parameters.length - 1].isVarArgs()) {
      return IntStream
        .rangeClosed(parameters.length - 1, FunctionalExpressionKey.MAX_ARG_COUNT)
        .filter(i -> i > paramIndex)
        .toArray();
    }
    return new int[]{Math.min(parameters.length, FunctionalExpressionKey.MAX_ARG_COUNT)};
  }

  private static int[] getPossibleArgIndices(PsiParameter parameter, int paramIndex, int argCount) {
    if (parameter.isVarArgs()) {
      return IntStream
        .rangeClosed(paramIndex + 1, FunctionalExpressionKey.MAX_ARG_COUNT)
        .filter(i -> i < argCount)
        .toArray();
    }
    return new int[]{Math.min(paramIndex, FunctionalExpressionKey.MAX_ARG_COUNT)};
  }

  @NotNull
  private static Set<Module> getJava8Modules(Project project) {
    final boolean projectLevelIsHigh = PsiUtil.getLanguageLevel(project).isAtLeast(LanguageLevel.JDK_1_8);

    final Set<Module> highLevelModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
      if (extension != null) {
        final LanguageLevel level = extension.getLanguageLevel();
        if (level == null && projectLevelIsHigh || level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) {
          highLevelModules.add(module);
        }
      }
    }
    return highLevelModules;
  }

  private static Collection<PsiMethod> getCandidateMethodsWithSuitableParams(final PsiClass aClass,
                                                                             final Project project,
                                                                             final GlobalSearchScope useScope,
                                                                             final Set<VirtualFile> candidateFiles,
                                                                             int expectedFunExprParamsCount,
                                                                             boolean isVoid) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        if (!aClass.isValid()) return Collections.emptyList();

        GlobalSearchScope visibleFromCandidates = combineResolveScopes(project, candidateFiles);

        Set<String> usedMethodNames = new HashSet<>();
        StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FUNCTIONAL_EXPRESSIONS, key -> {
          ProgressManager.checkCanceled();
          if (key.canRepresent(expectedFunExprParamsCount, isVoid)) {
            usedMethodNames.add(key.methodName);
          }
          return true;
        }, useScope, null);

        Set<PsiMethod> methods = ContainerUtil.newLinkedHashSet();
        Processor<PsiMethod> methodProcessor = method -> {
          if (usedMethodNames.contains(method.getName())) {
            methods.add(method);
          }
          return true;
        };

        StubIndexKey<String, PsiMethod> key = JavaMethodParameterTypesIndex.getInstance().getKey();
        StubIndex index = StubIndex.getInstance();
        index.processElements(key, aClass.getName(), project, useScope.intersectWith(visibleFromCandidates), PsiMethod.class, methodProcessor);
        index.processElements(key, JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project, visibleFromCandidates, PsiMethod.class, methodProcessor);
        LOG.info("#methods: " + methods.size());
        return methods;
      }
    });
  }

  @NotNull
  private static GlobalSearchScope combineResolveScopes(Project project, Set<VirtualFile> candidateFiles) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    Set<GlobalSearchScope> resolveScopes = ContainerUtil.newLinkedHashSet(ContainerUtil.mapNotNull(candidateFiles, file -> {
      PsiFile psiFile = file.isValid() ? psiManager.findFile(file) : null;
      return psiFile == null ? null : psiFile.getResolveScope();
    }));
    return GlobalSearchScope.union(resolveScopes.toArray(new GlobalSearchScope[resolveScopes.size()]));
  }

  @NotNull
  private static Set<VirtualFile> getFilesWithFunctionalExpressionsScope(Project project, GlobalSearchScope useScope) {
    final Set<VirtualFile> files = ContainerUtil.newLinkedHashSet();
    final PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(project);
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "::", processor);
    helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "->", processor);
    return files;
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

  private static boolean canPassFunctionalExpression(PsiClass sam, PsiParameter parameter) {
    PsiType paramType = parameter.getType();
    if (paramType instanceof PsiEllipsisType) {
      paramType = ((PsiEllipsisType)paramType).getComponentType();
    }
    PsiClass functionalCandidate = PsiUtil.resolveClassInClassTypeOnly(paramType);
    if (functionalCandidate instanceof PsiTypeParameter) {
      return InheritanceUtil.isInheritorOrSelf(sam, PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(paramType)), true);
    }

    return InheritanceUtil.isInheritorOrSelf(functionalCandidate, sam, true);
  }
}
