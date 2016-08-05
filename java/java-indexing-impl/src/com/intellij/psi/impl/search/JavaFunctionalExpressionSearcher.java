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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.AccessToken;
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
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey.CallLocation;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey.Location;
import com.intellij.psi.impl.java.stubs.JavaMethodElementType;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.*;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.*;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher");
  /**
   * The least number of candidate files with functional expressions that directly scanning them becomes expensive
   * and more advanced ways of searching become necessary: e.g. first searching for methods where the functional interface class is used
   * and then for their usages,
   */
  public static final int SMART_SEARCH_THRESHOLD = 5;
  private static final java.util.HashSet<String> KNOWN_STREAM_CLASSES =
    ContainerUtil.newHashSet(Stream.class.getName(),
                             IntStream.class.getName(), DoubleStream.class.getName(), LongStream.class.getName());

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
      SamDescriptor descriptor = ReadAction.compute(() -> {
        if (!funInterface.isValid()) return null;

        final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(funInterface);
        LOG.assertTrue(functionalInterfaceMethod != null);
        final PsiType samType = functionalInterfaceMethod.getReturnType();
        if (samType == null) return null;

        return new SamDescriptor(funInterface, functionalInterfaceMethod, samType,
                                 convertToGlobalScope(funInterface.getProject(),
                                                      funInterface.getUseScope().intersectWith(queryParameters.getEffectiveSearchScope())));
      });
      if (descriptor == null || !descriptor.search(consumer, highLevelModules)) return;
    }
  }

  private static boolean processFile(@NotNull Processor<PsiFunctionalExpression> consumer,
                                     PsiClass samClass,
                                     VirtualFile vFile, Collection<Integer> offsets) {
    return ReadAction.compute(() -> {
      if (!samClass.isValid()) return true;

      PsiFile file = samClass.getManager().findFile(vFile);
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

        if (InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType()), samClass, true)) {
          if (!consumer.process(expression)) {
            return false;
          }
        }
      }

      return true;
    });
  }

  @NotNull
  private static MultiMap<VirtualFile, Integer> getCandidateOffsets(SamDescriptor descriptor, MultiMap<Location, GlobalSearchScope> queries) {
    MultiMap<VirtualFile, Integer> result = MultiMap.create();
    for (Location location : queries.keySet()) {
      MultiMap<VirtualFile, Integer> offsets = getOffsetsForLocation(descriptor, location, queries.get(location));
      result.putAllValues(offsets);
      if (LOG.isDebugEnabled()) {
        logIfLarge(offsets, location);
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("checking " + result.values().size() + " fun-expressions in " + result.keySet().size() + " files");
    }
    return result;
  }

  private static void logIfLarge(MultiMap<VirtualFile, Integer> offsets, Location location) {
    int delta = offsets.values().size();
    if (delta > 5) {
      String sample = offsets.entrySet().stream().limit(10).map(e -> e.getKey().getName() + "->" + e.getValue()).collect(Collectors.joining(", "));
      LOG.debug(delta + " expressions for " + location + "; " + sample);
    }
  }

  @NotNull
  private static MultiMap<VirtualFile, Integer> getOffsetsForLocation(SamDescriptor descriptor, Location location, Collection<GlobalSearchScope> scopes) {
    MultiMap<VirtualFile, Integer> map = MultiMap.create();
    ReadAction.run(() -> {
      ProgressManager.checkCanceled();
      GlobalSearchScope combinedScope = descriptor.useScope.intersectWith(
        GlobalSearchScope.union(scopes.toArray(new GlobalSearchScope[0])));
      for (FunctionalExpressionKey key : descriptor.generateKeys(location)) {
        FileBasedIndex.getInstance().processValues(JavaFunctionalExpressionIndex.INDEX_ID, key, null, (file, offsets) -> {
          for (int i : offsets.toNativeArray()) {
            map.putValue(file, i);
          }
          return true;
        }, new JavaSourceFilterScope(combinedScope));
      }
    });
    return map;
  }

  @NotNull
  private static MultiMap<Location, GlobalSearchScope> collectQueryKeys(SamDescriptor descriptor, Iterable<PsiMethod> methods) {
    MultiMap<Location, GlobalSearchScope> queries = MultiMap.createSet();
    queries.putValue(Location.UNKNOWN, descriptor.useScope);
    queries.putValue(new FunctionalExpressionKey.TypedLocation(descriptor.getClassName()), descriptor.useScope);

    for (PsiMethod psiMethod : methods) {
      ReadAction.run(() -> {
        if (!psiMethod.isValid()) return;

        GlobalSearchScope methodUseScope = getGlobalUseScope(psiMethod);
        for (Location location : getPossibleCallLocations(descriptor.samClass, psiMethod)) {
          queries.putValue(location, methodUseScope);
        }
      });
    }
    return queries;
  }

  @NotNull
  private static GlobalSearchScope getGlobalUseScope(PsiMethod method) {
    if (PsiTreeUtil.getContextOfType(method, PsiAnonymousClass.class) != null || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      // don't call method.getUseScope as it'll be too specific and might cause stub-AST switch
      VirtualFile vFile = PsiUtilCore.getVirtualFile(method);
      if (vFile != null) {
        return GlobalSearchScope.fileScope(method.getProject(), vFile);
      }
    }

    return convertToGlobalScope(method.getProject(), method.getUseScope());
  }

  @NotNull
  private static Set<Location> getPossibleCallLocations(PsiClass samClass, PsiMethod calledMethod) {
    Set<Location> keys = new HashSet<>();

    String samName = samClass.getQualifiedName();
    boolean includeStreamApi = samName != null && samName.startsWith("java.util.function.") ||
                               hasStreamLikeApi(samClass.getProject());

    String methodName = calledMethod.getName();
    PsiParameter[] parameters = calledMethod.getParameterList().getParameters();
    for (int paramIndex = 0; paramIndex < parameters.length; paramIndex++) {
      PsiParameter parameter = parameters[paramIndex];
      if (canPassFunctionalExpression(samClass, parameter)) {
        for (int argCount : getPossibleArgCounts(parameters, paramIndex)) {
          for (int argIndex : getPossibleArgIndices(parameter, paramIndex, argCount)) {
            keys.add(new CallLocation(methodName, argCount, argIndex, false));
            if (includeStreamApi) {
              keys.add(new CallLocation(methodName, argCount, argIndex, true));
            }
          }
        }
      }
    }

    return keys;
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

  @NotNull
  private static Set<String> collectMethodNamesCalledWithFunExpressions(SamDescriptor descriptor) {
    Set<String> usedMethodNames = new HashSet<>();
    ReadAction.run(() -> FileBasedIndex.getInstance().processAllKeys(JavaFunctionalExpressionIndex.INDEX_ID, key -> {
      ProgressManager.checkCanceled();
      if (key.canRepresent(descriptor.samParamCount, descriptor.booleanCompatible, descriptor.isVoid) &&
          key.location instanceof CallLocation) {
        usedMethodNames.add(((CallLocation)key.location).methodName);
      }
      return true;
    }, descriptor.useScope, null));
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

    private boolean search(@NotNull Processor<PsiFunctionalExpression> consumer, Set<Module> highLevelModules) {
      GlobalSearchScope visibleFromCandidates = ReadAction.compute(
        () -> samClass.isValid() ? combineResolveScopes(highLevelModules, samClass) : null);
      if (visibleFromCandidates == null) return true;

      Set<String> usedMethodNames = collectMethodNamesCalledWithFunExpressions(this);
      Set<PsiMethod> exactTypeMethods = getMethodsWithParameterType(usedMethodNames, useScope.intersectWith(visibleFromCandidates), getClassName());
      Set<PsiMethod> genericMethods = getMethodsWithParameterType(usedMethodNames, visibleFromCandidates, JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME);
      LOG.debug("#methods: " + (exactTypeMethods.size() + genericMethods.size()));

      MultiMap<Location, GlobalSearchScope> queries = collectQueryKeys(this, ContainerUtil.concat(exactTypeMethods, genericMethods));
      return processLikelyFirst(getMostLikelyFiles(exactTypeMethods), consumer, queries);
    }

    private boolean processLikelyFirst(Set<VirtualFile> likelyFiles,
                                       @NotNull Processor<PsiFunctionalExpression> consumer,
                                       MultiMap<Location, GlobalSearchScope> queries) {
      MultiMap<VirtualFile, Integer> file2Offsets = getCandidateOffsets(this, queries);
      for (VirtualFile file : likelyFiles) {
        Collection<Integer> offsets = file2Offsets.remove(file);
        if (offsets != null && !processFile(consumer, samClass, file, offsets)) {
          return false;
        }
      }

      for (Map.Entry<VirtualFile, Collection<Integer>> entry : file2Offsets.entrySet()) {
        if (!processFile(consumer, samClass, entry.getKey(), entry.getValue())) {
          return false;
        }
      }
      return true;
    }

    @NotNull
    private Set<VirtualFile> getMostLikelyFiles(Set<PsiMethod> exactTypeMethods) {
      Set<VirtualFile> files = ContainerUtil.newLinkedHashSet();
      ReadAction.run(() -> {
        if (!samClass.isValid()) return;

        Set<String> likelyNames = ContainerUtil.newLinkedHashSet(getClassName());
        for (PsiMethod method : exactTypeMethods) {
          likelyNames.add(method.getName());
        }

        PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.SERVICE.getInstance(samClass.getProject());
        Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
        for (String name : likelyNames) {
          helper.processFilesWithText(this.useScope, UsageSearchContext.IN_CODE, true, name, processor);
        }
      });
      return files;
    }

    private Set<PsiMethod> getMethodsWithParameterType(Set<String> usedMethodNames,
                                                       GlobalSearchScope scope,
                                                       String type) {
      Set<PsiMethod> methods = new HashSet<>();
      ReadAction.run(() -> {
        if (!samClass.isValid()) return;

        StubIndexKey<String, PsiMethod> key = JavaMethodParameterTypesIndex.getInstance().getKey();
        StubIndex.getInstance().processElements(key, type, samClass.getProject(), scope, PsiMethod.class, method -> {
          ProgressManager.checkCanceled();
          if (usedMethodNames.contains(method.getName())) {
            methods.add(method);
          }

          return true;
        });
      });
      return methods;
    }

    @NotNull
    String getClassName() {
      return assertNotNull(samClass.getName());
    }

    List<FunctionalExpressionKey> generateKeys(Location location) {
      List<FunctionalExpressionKey> result = new ArrayList<>();
      for (int lambdaParamCount : new int[]{FunctionalExpressionKey.UNKNOWN_PARAM_COUNT, samParamCount}) {
        result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.UNKNOWN, location));
        if (isVoid) {
          result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.VOID, location));
        } else {
          if (booleanCompatible) {
            result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.BOOLEAN, location));
          }
          result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.NON_VOID, location));
        }
      }
      return result;
    }
  }

  @VisibleForTesting
  public static boolean hasStreamLikeApi(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(hasStreamLikeApi(project, "Arrays", "stream") || hasStreamLikeApi(project, "Stream", "of"),
                                        PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  private static boolean hasStreamLikeApi(Project project, String qualifier, String methodName) {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    for (PsiClass candidate : cache.getClassesByName(qualifier, GlobalSearchScope.allScope(project))) {
      if (hasMethodWithNonStreamType(methodName, candidate)) return true;
    }

    for (PsiField field : cache.getFieldsByName(qualifier, GlobalSearchScope.allScope(project))) {
      PsiClass fieldType = PsiUtil.resolveClassInClassTypeOnly(field.getType());
      if (fieldType == null || fieldType instanceof PsiTypeParameter || hasMethodWithNonStreamType(methodName, fieldType)) return true;
    }

    return false;
  }

  private static boolean hasMethodWithNonStreamType(@NotNull String methodName, @NotNull PsiClass candidate) {
    for (PsiMethod method : candidate.findMethodsByName(methodName, true)) {
      PsiClass returnType = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      if (returnType == null || !KNOWN_STREAM_CLASSES.contains(returnType.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}