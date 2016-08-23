/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.Stack;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;

/**
 * @author lambdamix
 */
public class ProjectBytecodeAnalysis {
  public static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis");
  public static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
  public static final String NULLABLE_METHOD = "java.annotations.inference.nullable.method";
  public static final String NULLABLE_METHOD_TRANSITIVITY = "java.annotations.inference.nullable.method.transitivity";
  public static final int EQUATIONS_LIMIT = 1000;
  private final Project myProject;
  private final boolean nullableMethod;
  private final boolean nullableMethodTransitivity;

  public static ProjectBytecodeAnalysis getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBytecodeAnalysis.class);
  }

  public ProjectBytecodeAnalysis(Project project) {
    myProject = project;
    nullableMethod = Registry.is(NULLABLE_METHOD);
    nullableMethodTransitivity = Registry.is(NULLABLE_METHOD_TRANSITIVITY);
  }

  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return null;
    }
    if (annotationFQN.equals(AnnotationUtil.NOT_NULL) || annotationFQN.equals(AnnotationUtil.NULLABLE) || annotationFQN.equals(ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
      PsiAnnotation[] annotations = findInferredAnnotations(listOwner);
      for (PsiAnnotation annotation : annotations) {
        if (annotationFQN.equals(annotation.getQualifiedName())) {
          return annotation;
        }
      }
      return null;
    }
    else {
      return null;
    }
  }

  @NotNull
  public PsiAnnotation[] findInferredAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    return CachedValuesManager.getCachedValue(listOwner,
                                              () -> CachedValueProvider.Result.create(collectInferredAnnotations(listOwner), listOwner));
  }

  /**
   * Ignore inside android.jar because all class files there are dummy and contain no code at all.
   * Rely on the fact that it's always located at .../platforms/android-.../android.jar!/
   */
  private static boolean isInsideDummyAndroidJar(@Nullable PsiFile psiFile) {
    VirtualFile file = psiFile == null ? null : psiFile.getVirtualFile();
    if (file == null) return false;

    String path = file.getPath();
    int index = path.indexOf("/android.jar!/");
    return index > 0 && path.lastIndexOf("platforms/android-", index) > 0;
  }

  @NotNull
  private PsiAnnotation[] collectInferredAnnotations(PsiModifierListOwner listOwner) {
    if (isInsideDummyAndroidJar(listOwner.getContainingFile())) return PsiAnnotation.EMPTY_ARRAY;

    try {
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      HKey primaryKey = getKey(listOwner, md);
      if (primaryKey == null) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      if (listOwner instanceof PsiMethod) {
        ArrayList<HKey> allKeys = collectMethodKeys((PsiMethod)listOwner, primaryKey);
        MethodAnnotations methodAnnotations = loadMethodAnnotations((PsiMethod)listOwner, primaryKey, allKeys);
        return toPsi(primaryKey, methodAnnotations);
      } else if (listOwner instanceof PsiParameter) {
        ParameterAnnotations parameterAnnotations = loadParameterAnnotations(primaryKey);
        return toPsi(parameterAnnotations);
      }
      return PsiAnnotation.EMPTY_ARRAY;
    }
    catch (EquationsLimitException e) {
      if (LOG.isDebugEnabled()) {
        String externalName = PsiFormatUtil.getExternalName(listOwner, false, Integer.MAX_VALUE);
        LOG.debug("Too many equations for " + externalName);
      }
      return PsiAnnotation.EMPTY_ARRAY;
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
      return PsiAnnotation.EMPTY_ARRAY;
    }
  }

  /**
   * Converts inferred method annotations to Psi annotations
   *
   * @param primaryKey primary compressed key for method
   * @param methodAnnotations inferred annotations
   * @return Psi annotations
   */
  private PsiAnnotation[] toPsi(HKey primaryKey, MethodAnnotations methodAnnotations) {
    boolean notNull = methodAnnotations.notNulls.contains(primaryKey);
    boolean nullable = methodAnnotations.nullables.contains(primaryKey);
    boolean pure = methodAnnotations.pures.contains(primaryKey);
    String contractValues = methodAnnotations.contractsValues.get(primaryKey);
    String contractPsiText = null;

    if (contractValues != null) {
      contractPsiText = pure ? "value=" + contractValues + ",pure=true" : contractValues;
    } else if (pure) {
      contractPsiText = "pure=true";
    }

    PsiAnnotation psiAnnotation =
      contractPsiText == null ? null : createContractAnnotation(contractPsiText);

    if (notNull && psiAnnotation != null) {
      return new PsiAnnotation[]{
        getNotNullAnnotation(), psiAnnotation
      };
    }
    if (nullable && psiAnnotation != null) {
      return new PsiAnnotation[]{
        getNullableAnnotation(), psiAnnotation
      };
    }
    if (notNull) {
      return new PsiAnnotation[]{
        getNotNullAnnotation()
      };
    }
    if (nullable) {
      return new PsiAnnotation[]{
        getNullableAnnotation()
      };
    }
    if (psiAnnotation != null) {
      return new PsiAnnotation[]{
        psiAnnotation
      };
    }
    return PsiAnnotation.EMPTY_ARRAY;
  }

  /**
   * Converts inferred parameter annotations to Psi annotations
   *
   * @param parameterAnnotations inferred parameter annotations
   * @return Psi annotations
   */
  private PsiAnnotation[] toPsi(ParameterAnnotations parameterAnnotations) {
    if (parameterAnnotations.notNull) {
      return new PsiAnnotation[]{
        getNotNullAnnotation()
      };
    }
    else if (parameterAnnotations.nullable) {
      return new PsiAnnotation[]{
        getNullableAnnotation()
      };
    }
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public PsiAnnotation getNotNullAnnotation() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(createAnnotationFromText("@" + AnnotationUtil.NOT_NULL), ModificationTracker.NEVER_CHANGED));
  }

  public PsiAnnotation getNullableAnnotation() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(createAnnotationFromText("@" + AnnotationUtil.NULLABLE), ModificationTracker.NEVER_CHANGED));
  }

  public PsiAnnotation createContractAnnotation(String contractValue) {
    Map<String, PsiAnnotation> cache = CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Map<String, PsiAnnotation> map = new ConcurrentFactoryMap<String, PsiAnnotation>() {
        @Nullable
        @Override
        protected PsiAnnotation create(String attrs) {
          return createAnnotationFromText("@org.jetbrains.annotations.Contract(" + attrs + ")");
        }
      };
      return CachedValueProvider.Result.create(map, ModificationTracker.NEVER_CHANGED);
    });
    return cache.get(contractValue);
  }

  @Nullable
  public static HKey getKey(@NotNull PsiModifierListOwner owner, MessageDigest md) {
    LOG.assertTrue(owner instanceof PsiCompiledElement, owner);
    if (owner instanceof PsiMethod) {
      return BytecodeAnalysisConverter.psiKey((PsiMethod)owner, Out, md);
    }
    if (owner instanceof PsiParameter) {
      PsiElement parent = owner.getParent();
      if (parent instanceof PsiParameterList) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethod) {
          final int index = ((PsiParameterList)parent).getParameterIndex((PsiParameter)owner);
          return BytecodeAnalysisConverter.psiKey((PsiMethod)gParent, new In(index, In.NOT_NULL_MASK), md);
        }
      }
    }
    return null;
  }

  /**
   * Collects all (starting) keys needed to infer all pieces of method annotations.
   *
   * @param method Psi method for which annotations are being inferred
   * @param primaryKey primary compressed key for this method
   * @return compressed keys for this method
   */
  public static ArrayList<HKey> collectMethodKeys(@NotNull PsiMethod method, HKey primaryKey) {
    return BytecodeAnalysisConverter.mkInOutKeys(method, primaryKey);
  }

  private ParameterAnnotations loadParameterAnnotations(@NotNull HKey notNullKey)
    throws EquationsLimitException {

    Map<Bytes, List<HEquations>> equationsCache = new HashMap<>();

    final Solver notNullSolver = new Solver(new ELattice<>(Value.NotNull, Value.Top), Value.Top);
    collectEquations(Collections.singletonList(notNullKey), notNullSolver, equationsCache);

    Map<HKey, Value> notNullSolutions = notNullSolver.solve();
    // subtle point
    boolean notNull =
      (Value.NotNull == notNullSolutions.get(notNullKey)) || (Value.NotNull == notNullSolutions.get(notNullKey.mkUnstable()));

    final Solver nullableSolver = new Solver(new ELattice<>(Value.Null, Value.Top), Value.Top);
    final HKey nullableKey = new HKey(notNullKey.key, notNullKey.dirKey + 1, true, false);
    collectEquations(Collections.singletonList(nullableKey), nullableSolver, equationsCache);
    Map<HKey, Value> nullableSolutions = nullableSolver.solve();
    // subtle point
    boolean nullable =
      (Value.Null == nullableSolutions.get(nullableKey)) || (Value.Null == nullableSolutions.get(nullableKey.mkUnstable()));
    return new ParameterAnnotations(notNull, nullable);
  }

  private MethodAnnotations loadMethodAnnotations(@NotNull PsiMethod owner, @NotNull HKey key, ArrayList<HKey> allKeys)
    throws EquationsLimitException {
    MethodAnnotations result = new MethodAnnotations();
    Map<Bytes, List<HEquations>> equationsCache = new HashMap<>();

    final Solver outSolver = new Solver(new ELattice<>(Value.Bot, Value.Top), Value.Top);
    final PuritySolver puritySolver = new PuritySolver();
    collectEquations(allKeys, outSolver, equationsCache);
    collectPurityEquations(key.updateDirection(BytecodeAnalysisConverter.mkDirectionKey(Pure)), puritySolver, equationsCache);

    Map<HKey, Value> solutions = outSolver.solve();
    Map<HKey, Set<HEffectQuantum>> puritySolutions = puritySolver.solve();

    int arity = owner.getParameterList().getParameters().length;
    BytecodeAnalysisConverter.addMethodAnnotations(solutions, result, key, arity);
    BytecodeAnalysisConverter.addEffectAnnotations(puritySolutions, result, key, arity);


    if (nullableMethod) {
      final Solver nullableMethodSolver = new Solver(new ELattice<>(Value.Bot, Value.Null), Value.Bot);
      HKey nullableKey = key.updateDirection(BytecodeAnalysisConverter.mkDirectionKey(NullableOut));
      if (nullableMethodTransitivity) {
        collectEquations(Collections.singletonList(nullableKey), nullableMethodSolver, equationsCache);
      }
      else {
        collectSingleEquation(nullableKey, nullableMethodSolver, equationsCache);
      }
      Map<HKey, Value> nullableSolutions = nullableMethodSolver.solve();
      if (nullableSolutions.get(nullableKey) == Value.Null || nullableSolutions.get(nullableKey.invertStability()) == Value.Null) {
        result.nullables.add(key);
      }
    }
    return result;
  }

  private void collectPurityEquations(HKey key, PuritySolver puritySolver, Map<Bytes, List<HEquations>> cache)
    throws EquationsLimitException {
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
    HashSet<HKey> queued = new HashSet<>();
    Stack<HKey> queue = new Stack<>();

    queue.push(key);
    queued.add(key);

    FileBasedIndex index = FileBasedIndex.getInstance();

    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      HKey hKey = queue.pop();
      Bytes bytes = new Bytes(hKey.key);

      List<HEquations> hEquationss = cache.get(bytes);
      if (hEquationss == null) {
        hEquationss = index.getValues(BytecodeAnalysisIndex.NAME, bytes, librariesScope);
        cache.put(bytes, hEquationss);
      }

      for (HEquations hEquations : hEquationss) {
        boolean stable = hEquations.stable;
        for (DirectionResultPair pair : hEquations.results) {
          int dirKey = pair.directionKey;
          if (dirKey == hKey.dirKey) {
            Set<HEffectQuantum> effects = ((HEffects)pair.hResult).effects;
            puritySolver.addEquation(new HKey(bytes.bytes, dirKey, stable, false), effects);
            for (HEffectQuantum effect : effects) {
              if (effect instanceof HEffectQuantum.CallQuantum) {
                HKey depKey = ((HEffectQuantum.CallQuantum)effect).key;
                if (!queued.contains(depKey)) {
                  queue.push(depKey);
                  queued.add(depKey);
                }
              }
            }
          }
        }
      }
    }
  }

  private void collectEquations(List<HKey> keys, Solver solver, @NotNull Map<Bytes, List<HEquations>> cache) throws EquationsLimitException {

    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
    HashSet<HKey> queued = new HashSet<>();
    Stack<HKey> queue = new Stack<>();

    for (HKey key : keys) {
      queue.push(key);
      queued.add(key);
    }

    FileBasedIndex index = FileBasedIndex.getInstance();

    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      HKey hKey = queue.pop();
      Bytes bytes = new Bytes(hKey.key);

      List<HEquations> hEquationss = cache.get(bytes);
      if (hEquationss == null) {
        hEquationss = index.getValues(BytecodeAnalysisIndex.NAME, bytes, librariesScope);
        cache.put(bytes, hEquationss);
      }

      for (HEquations hEquations : hEquationss) {
        boolean stable = hEquations.stable;
        for (DirectionResultPair pair : hEquations.results) {
          int dirKey = pair.directionKey;
          if (dirKey == hKey.dirKey) {
            HResult result = pair.hResult;

            solver.addEquation(new HEquation(new HKey(bytes.bytes, dirKey, stable, false), result));
            if (result instanceof HPending) {
              HPending pending = (HPending)result;
              for (HComponent component : pending.delta) {
                for (HKey depKey : component.ids) {
                  if (!queued.contains(depKey)) {
                    queue.push(depKey);
                    queued.add(depKey);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private void collectSingleEquation(HKey hKey, Solver solver, @NotNull Map<Bytes, List<HEquations>> cache) throws EquationsLimitException {
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);

    FileBasedIndex index = FileBasedIndex.getInstance();

    ProgressManager.checkCanceled();
    Bytes bytes = new Bytes(hKey.key);

    List<HEquations> hEquationss = cache.get(bytes);
    if (hEquationss == null) {
      hEquationss = index.getValues(BytecodeAnalysisIndex.NAME, bytes, librariesScope);
      cache.put(bytes, hEquationss);
    }

    for (HEquations hEquations : hEquationss) {
      boolean stable = hEquations.stable;
      for (DirectionResultPair pair : hEquations.results) {
        int dirKey = pair.directionKey;
        if (dirKey == hKey.dirKey) {
          HResult result = pair.hResult;
          solver.addEquation(new HEquation(new HKey(bytes.bytes, dirKey, stable, false), result));
        }
      }
    }
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(myProject).createAnnotationFromText(text, null);
    annotation.putUserData(INFERRED_ANNOTATION, Boolean.TRUE);
    ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
    return annotation;
  }
}

class MethodAnnotations {
  // @NotNull keys
  final Set<HKey> notNulls = new HashSet<>(1);
  // @Nullable keys
  final Set<HKey> nullables = new HashSet<>(1);
  // @Contract(pure=true) part of contract
  final Set<HKey> pures = new HashSet<>(1);
  // @Contracts
  final Map<HKey, String> contractsValues = new HashMap<>();
}

class ParameterAnnotations {
  final boolean notNull;
  final boolean nullable;

  ParameterAnnotations(boolean notNull, boolean nullable) {
    this.notNull = notNull;
    this.nullable = nullable;
  }
}

class EquationsLimitException extends Exception {}
