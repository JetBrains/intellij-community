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
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey.CallLocation;
import com.intellij.psi.impl.java.stubs.JavaMethodElementType;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.util.ObjectUtils.assertNotNull;

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
    final Set<Module> highLevelModules;
    final List<PsiClass> funInterfaces;

    try (AccessToken ignored = ReadAction.start()) {
      PsiClass aClass = queryParameters.getElementToSearch();
      if (!aClass.isValid() ||
          !aClass.isInterface() ||
          InjectedLanguageManager.getInstance(aClass.getProject()).isInjectedFragment(aClass.getContainingFile())) {
        return;
      }

      highLevelModules = getJava8Modules(aClass.getProject());
      if (highLevelModules.isEmpty()) return;

      funInterfaces = ContainerUtil.filter(processSubInterfaces(aClass), LambdaUtil::isFunctionalClass);
    }

    for (PsiClass funInterface : funInterfaces) {
      final GlobalSearchScope useScope;
      final int samParamCount;
      final PsiType samType;
      try (AccessToken ignored = ReadAction.start()) {
        if (!funInterface.isValid()) continue;

        useScope = convertToGlobalScope(funInterface.getProject(), funInterface.getUseScope().intersectWith(queryParameters.getEffectiveSearchScope()));

        final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(funInterface);
        LOG.assertTrue(functionalInterfaceMethod != null);
        samParamCount = functionalInterfaceMethod.getParameterList().getParameters().length;
        samType = functionalInterfaceMethod.getReturnType();
        if (samType == null) continue;
      }

      MultiMap<FunctionalExpressionKey, GlobalSearchScope> queries =
        collectQueryKeys(useScope, funInterface, samParamCount, samType, highLevelModules);

      for (PsiFunctionalExpression expression : getCandidates(useScope, funInterface.getProject(), queries)) {
        if (!processExpression(consumer, funInterface, expression)) {
          return;
        }
      }
    }
  }

  private static Boolean processExpression(@NotNull Processor<PsiFunctionalExpression> consumer,
                                           PsiClass aClass,
                                           PsiFunctionalExpression expression) {
    return ReadAction.compute(() -> {
      if (expression.isValid() &&
          InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType()), aClass, true)) {
        if (!consumer.process(expression)) {
          return false;
        }
      }

      return true;
    });
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
                                                                                       int samParamCount,
                                                                                       @NotNull PsiType samType,
                                                                                       Set<Module> candidateModules) {
    MultiMap<FunctionalExpressionKey, GlobalSearchScope> queries = MultiMap.createSet();

    ReadAction.run(() -> {
      if (!samType.isValid()) return;

      for (FunctionalExpressionKey key : generateKeys(samParamCount, samType, FunctionalExpressionKey.Location.UNKNOWN)) {
        queries.putValue(key, useScope); // check all fun-exprs that aren't inside calls or variables
      }

      for (FunctionalExpressionKey key : generateKeys(samParamCount, samType,
                                                      new FunctionalExpressionKey.VariableLocation(assertNotNull(aClass.getName())))) {
        queries.putValue(key, useScope);
      }
    });

    //collect all methods with parameter of functional interface or free type parameter type
    for (final PsiMethod psiMethod : getCandidateMethodsWithSuitableParams(aClass, useScope, candidateModules, samParamCount, samType)) {
      ReadAction.run(() -> {
        if (!psiMethod.isValid() || !samType.isValid()) return;

        final GlobalSearchScope methodUseScope = convertToGlobalScope(psiMethod.getProject(), psiMethod.getUseScope());
        for (FunctionalExpressionKey key : getQueryKeys(aClass, samParamCount, samType, psiMethod)) {
          queries.putValue(key, methodUseScope);
        }
      });
    }
    return queries;
  }

  @NotNull
  private static Set<FunctionalExpressionKey> getQueryKeys(PsiClass samClass,
                                                           int samParamCount,
                                                           PsiType samType,
                                                           PsiMethod calledMethod) {
    Set<FunctionalExpressionKey> keys = new HashSet<>();

    String methodName = calledMethod.getName();
    PsiParameter[] parameters = calledMethod.getParameterList().getParameters();
    for (int paramIndex = 0; paramIndex < parameters.length; paramIndex++) {
      PsiParameter parameter = parameters[paramIndex];
      if (canPassFunctionalExpression(samClass, parameter)) {
        for (int argCount : getPossibleArgCounts(parameters, paramIndex)) {
          for (int argIndex : getPossibleArgIndices(parameter, paramIndex, argCount)) {
            keys.addAll(generateKeys(samParamCount, samType, new CallLocation(methodName, argCount, argIndex)));
          }
        }
      }
    }

    return keys;
  }

  private static List<FunctionalExpressionKey> generateKeys(int samMethodParamsCount,
                                                            PsiType samType,
                                                            FunctionalExpressionKey.Location location) {
    List<FunctionalExpressionKey> result = new ArrayList<>();
    for (int lambdaParamCount : new int[]{FunctionalExpressionKey.UNKNOWN_PARAM_COUNT, samMethodParamsCount}) {
      result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.UNKNOWN, location));
      if (PsiType.VOID.equals(samType)) {
        result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.VOID, location));
      } else {
        if (FunctionalExpressionKey.isBooleanCompatible(samType)) {
          result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.BOOLEAN, location));
        }
        result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.NON_VOID, location));
      }
    }
    return result;
  }

  private static int[] getPossibleArgCounts(PsiParameter[] parameters, int paramIndex) {
    if (parameters[parameters.length - 1].isVarArgs()) {
      return IntStream
        .rangeClosed(parameters.length - 1, CallLocation.MAX_ARG_COUNT)
        .filter(i -> i > paramIndex)
        .toArray();
    }
    return new int[]{Math.min(parameters.length, CallLocation.MAX_ARG_COUNT)};
  }

  private static int[] getPossibleArgIndices(PsiParameter parameter, int paramIndex, int argCount) {
    if (parameter.isVarArgs()) {
      return IntStream
        .rangeClosed(paramIndex + 1, CallLocation.MAX_ARG_COUNT)
        .filter(i -> i < argCount)
        .toArray();
    }
    return new int[]{Math.min(paramIndex, CallLocation.MAX_ARG_COUNT)};
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

  private static Collection<PsiMethod> getCandidateMethodsWithSuitableParams(final PsiClass samClass,
                                                                             final GlobalSearchScope useScope,
                                                                             final Set<Module> candidateModules,
                                                                             int expectedFunExprParamsCount,
                                                                             PsiType samType) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        if (!samClass.isValid()) return Collections.emptyList();

        GlobalSearchScope visibleFromCandidates = combineResolveScopes(candidateModules, samClass);
        if (visibleFromCandidates == null) return Collections.emptyList();

        Set<String> usedMethodNames = collectMethodNamesCalledWithFunExpressions(samType, expectedFunExprParamsCount, useScope);

        Set<PsiMethod> methods = ContainerUtil.newLinkedHashSet();
        Processor<PsiMethod> methodProcessor = method -> {
          if (usedMethodNames.contains(method.getName())) {
            methods.add(method);
          }
          return true;
        };

        StubIndexKey<String, PsiMethod> key = JavaMethodParameterTypesIndex.getInstance().getKey();
        StubIndex index = StubIndex.getInstance();
        Project project = samClass.getProject();
        index.processElements(key, assertNotNull(samClass.getName()), project, useScope.intersectWith(visibleFromCandidates), PsiMethod.class, methodProcessor);
        index.processElements(key, JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project, visibleFromCandidates, PsiMethod.class, methodProcessor);
        LOG.info("#methods: " + methods.size());
        return methods;
      }
    });
  }

  @NotNull
  private static Set<String> collectMethodNamesCalledWithFunExpressions(PsiType samType, int samParamCount, GlobalSearchScope useScope) {
    boolean booleanCompatible = FunctionalExpressionKey.isBooleanCompatible(samType);
    boolean isVoid = PsiType.VOID.equals(samType);

    Set<String> usedMethodNames = new HashSet<>();
    StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FUNCTIONAL_EXPRESSIONS, key -> {
      ProgressManager.checkCanceled();
      if (key.canRepresent(samParamCount, booleanCompatible, isVoid) && key.location instanceof CallLocation) {
        usedMethodNames.add(((CallLocation)key.location).methodName);
      }
      return true;
    }, useScope, null);
    return usedMethodNames;
  }

  @Nullable
  private static GlobalSearchScope combineResolveScopes(Set<Module> candidateModules, PsiClass samClass) {
    List<GlobalSearchScope> scopes = candidateModules.stream()
      .map(GlobalSearchScope::moduleWithDependenciesAndLibrariesScope)
      .filter(s -> PsiSearchScopeUtil.isInScope(s, samClass))
      .collect(Collectors.toList());
    return scopes.isEmpty() ? null : GlobalSearchScope.union(scopes.toArray(new GlobalSearchScope[scopes.size()]));
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

  private static Set<PsiClass> processSubInterfaces(PsiClass base) {
    Set<PsiClass> result = new HashSet<>();
    new Object() {
      void visit(PsiClass c) {
        if (!result.add(c)) return;

        DirectClassInheritorsSearch.search(c).forEach(candidate -> {
          if (candidate.isInterface() && isFunctionalCompatible(candidate)) {
            visit(candidate);
          }
          return true;
        });
      }
    }.visit(base);
    return result;
  }

  private static boolean isFunctionalCompatible(PsiClass candidate) {
    return LambdaUtil.isFunctionalClass(candidate) ||
           Arrays.stream(candidate.getAllMethods()).filter(m -> !m.hasModifierProperty(PsiModifier.DEFAULT)).count() == 0;
  }
}