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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;

/**
 * @author lambdamix
 */
public class ProjectBytecodeAnalysis {
  /**
   * Setting this to true will disable persistent index and disable hashing which could be really useful for debugging
   * (if behaviour to debug does not depend on the index/externalization/etc.)
   */
  private static final boolean SKIP_INDEX = false;
  public static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis");
  public static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
  public static final String NULLABLE_METHOD = "java.annotations.inference.nullable.method";
  public static final String NULLABLE_METHOD_TRANSITIVITY = "java.annotations.inference.nullable.method.transitivity";
  public static final int EQUATIONS_LIMIT = 1000;
  private final Project myProject;
  private final boolean nullableMethod;
  private final boolean nullableMethodTransitivity;
  private final EquationProvider<?> myEquationProvider;

  public static ProjectBytecodeAnalysis getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBytecodeAnalysis.class);
  }

  public ProjectBytecodeAnalysis(Project project) {
    myProject = project;
    //noinspection ConstantConditions
    myEquationProvider = SKIP_INDEX ? new PlainEquationProvider(myProject) : new IndexedEquationProvider(myProject);
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
      EKey primaryKey = getKey(listOwner, md);
      if (primaryKey == null) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      if (listOwner instanceof PsiMethod) {
        ArrayList<EKey> allKeys = collectMethodKeys((PsiMethod)listOwner, primaryKey);
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
  }

  /**
   * Converts inferred method annotations to Psi annotations
   *
   * @param primaryKey primary compressed key for method
   * @param methodAnnotations inferred annotations
   * @return Psi annotations
   */
  @NotNull
  private PsiAnnotation[] toPsi(EKey primaryKey, MethodAnnotations methodAnnotations) {
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
  @NotNull
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
      Map<String, PsiAnnotation> map =
        ConcurrentFactoryMap.createConcurrentMap(attrs -> createAnnotationFromText("@org.jetbrains.annotations.Contract(" + attrs + ")"));
      return CachedValueProvider.Result.create(map, ModificationTracker.NEVER_CHANGED);
    });
    return cache.get(contractValue);
  }

  @Nullable
  public EKey getKey(@NotNull PsiModifierListOwner owner, MessageDigest md) {
    LOG.assertTrue(owner instanceof PsiCompiledElement, owner);
    if (owner instanceof PsiMethod) {
      EKey key = BytecodeAnalysisConverter.psiKey((PsiMethod)owner, Out);
      return key == null ? null : myEquationProvider.adaptKey(key, md);
    }
    if (owner instanceof PsiParameter) {
      PsiElement parent = owner.getParent();
      if (parent instanceof PsiParameterList) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethod) {
          final int index = ((PsiParameterList)parent).getParameterIndex((PsiParameter)owner);
          EKey key = BytecodeAnalysisConverter.psiKey((PsiMethod)gParent, new In(index, false));
          return key == null ? null : myEquationProvider.adaptKey(key, md);
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
  public static ArrayList<EKey> collectMethodKeys(@NotNull PsiMethod method, EKey primaryKey) {
    return BytecodeAnalysisConverter.mkInOutKeys(method, primaryKey);
  }

  private ParameterAnnotations loadParameterAnnotations(@NotNull EKey notNullKey)
    throws EquationsLimitException {

    final Solver notNullSolver = new Solver(new ELattice<>(Value.NotNull, Value.Top), Value.Top);
    collectEquations(Collections.singletonList(notNullKey), notNullSolver);

    Map<EKey, Value> notNullSolutions = notNullSolver.solve();
    // subtle point
    boolean notNull =
      (Value.NotNull == notNullSolutions.get(notNullKey)) || (Value.NotNull == notNullSolutions.get(notNullKey.mkUnstable()));

    final Solver nullableSolver = new Solver(new ELattice<>(Value.Null, Value.Top), Value.Top);
    final EKey nullableKey = new EKey(notNullKey.method, notNullKey.dirKey + 1, true, false);
    collectEquations(Collections.singletonList(nullableKey), nullableSolver);
    Map<EKey, Value> nullableSolutions = nullableSolver.solve();
    // subtle point
    boolean nullable =
      (Value.Null == nullableSolutions.get(nullableKey)) || (Value.Null == nullableSolutions.get(nullableKey.mkUnstable()));
    return new ParameterAnnotations(notNull, nullable);
  }

  private MethodAnnotations loadMethodAnnotations(@NotNull PsiMethod owner, @NotNull EKey key, ArrayList<EKey> allKeys)
    throws EquationsLimitException {
    MethodAnnotations result = new MethodAnnotations();

    final PuritySolver puritySolver = new PuritySolver();
    collectPurityEquations(key.withDirection(Pure), puritySolver);

    Map<EKey, Effects> puritySolutions = puritySolver.solve();

    int arity = owner.getParameterList().getParameters().length;
    BytecodeAnalysisConverter.addEffectAnnotations(puritySolutions, result, key, owner.isConstructor());

    EKey failureKey = key.withDirection(Throw);
    final Solver failureSolver = new Solver(new ELattice<>(Value.Fail, Value.Top), Value.Top);
    collectEquations(Collections.singletonList(failureKey), failureSolver);
    if (failureSolver.solve().get(failureKey) == Value.Fail) {
      // Always failing method
      result.contractsValues.put(key, StreamEx.constant("_", arity).joining(",", "\"", "->fail\""));
    } else {
      final Solver outSolver = new Solver(new ELattice<>(Value.Bot, Value.Top), Value.Top);
      collectEquations(allKeys, outSolver);
      Map<EKey, Value> solutions = outSolver.solve();
      BytecodeAnalysisConverter.addMethodAnnotations(solutions, result, key, arity);
    }

    if (nullableMethod) {
      final Solver nullableMethodSolver = new Solver(new ELattice<>(Value.Bot, Value.Null), Value.Bot);
      EKey nullableKey = key.withDirection(NullableOut);
      if (nullableMethodTransitivity) {
        collectEquations(Collections.singletonList(nullableKey), nullableMethodSolver);
      }
      else {
        collectSingleEquation(nullableKey, nullableMethodSolver);
      }
      Map<EKey, Value> nullableSolutions = nullableMethodSolver.solve();
      if (nullableSolutions.get(nullableKey) == Value.Null || nullableSolutions.get(nullableKey.invertStability()) == Value.Null) {
        result.nullables.add(key);
      }
    }
    return result;
  }

  private static EKey withStability(EKey key, boolean stability) {
    return new EKey(key.method, key.dirKey, stability, false);
  }

  private void collectPurityEquations(EKey key, PuritySolver puritySolver)
    throws EquationsLimitException {
    HashSet<EKey> queued = new HashSet<>();
    Stack<EKey> queue = new Stack<>();

    queue.push(key);
    queued.add(key);

    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      EKey curKey = queue.pop();

      boolean stable = true;
      Effects combined = null;
      for (Equations equations : myEquationProvider.getEquations(curKey.method)) {
        stable &= equations.stable;
        Effects effects = (Effects)equations.find(curKey.getDirection())
          .orElseGet(() -> new Effects(DataValue.UnknownDataValue1, Effects.TOP_EFFECTS));
        combined = combined == null ? effects : combined.combine(effects);
      }
      if (combined != null) {
        combined.dependencies().filter(queued::add).forEach(queue::push);
        puritySolver.addEquation(withStability(curKey, stable), combined);
      }
    }
  }

  private void collectEquations(List<EKey> keys, Solver solver) throws EquationsLimitException {
    HashSet<EKey> queued = new HashSet<>();
    Stack<EKey> queue = new Stack<>();

    for (EKey key : keys) {
      queue.push(key);
      queued.add(key);
    }

    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      EKey curKey = queue.pop();

      for (Equations equations : myEquationProvider.getEquations(curKey.method)) {
        Result result = equations.find(curKey.getDirection()).orElseGet(solver::getUnknownResult);
        solver.addEquation(new Equation(withStability(curKey, equations.stable), result));
        result.dependencies().filter(queued::add).forEach(queue::push);
      }
    }
  }

  private void collectSingleEquation(EKey curKey, Solver solver) throws EquationsLimitException {
    ProgressManager.checkCanceled();

    for (Equations equations : myEquationProvider.getEquations(curKey.method)) {
      Result result = equations.find(curKey.getDirection()).orElseGet(solver::getUnknownResult);
      solver.addEquation(new Equation(withStability(curKey, equations.stable), result));
    }
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(myProject).createAnnotationFromText(text, null);
    annotation.putUserData(INFERRED_ANNOTATION, Boolean.TRUE);
    ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
    return annotation;
  }

  static abstract class EquationProvider<T extends MethodDescriptor> {
    final Map<T, List<Equations>> myEquationCache = ContainerUtil.createConcurrentSoftValueMap();
    final Project myProject;

    EquationProvider(Project project) {
      myProject = project;
      project.getMessageBus().connect().subscribe(PsiModificationTracker.TOPIC, () -> myEquationCache.clear());
    }

    abstract EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest);

    abstract List<Equations> getEquations(MethodDescriptor method);
  }

  /**
   * PlainEquationProvider (used for debug purposes)
   * All EKey's are not hashed; persistent index is not used to store equations
   */
  static class PlainEquationProvider extends EquationProvider<Method> {
    PlainEquationProvider(Project project) {
      super(project);
    }

    @Override
    public EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest) {
      assert key.method instanceof Method;
      return key;
    }

    @Override
    public List<Equations> getEquations(MethodDescriptor methodDescriptor) {
      assert methodDescriptor instanceof Method;
      Method method = (Method)methodDescriptor;
      List<Equations> equations = myEquationCache.get(method);
      return equations == null ? loadEquations(method) : equations;
    }

    private VirtualFile findClassFile(String internalClassName) {
      String packageName = StringUtil.getPackageName(internalClassName, '/').replace('/', '.');
      String className = StringUtil.getShortName(internalClassName, '/');
      PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
      if (aPackage == null) {
        PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(StringUtil.getQualifiedName(packageName, className), GlobalSearchScope
          .allScope(myProject));
        if(psiClass != null) {
          PsiModifierListOwner compiledClass = PsiUtil.preferCompiledElement(psiClass);
          if(compiledClass instanceof ClsClassImpl) {
            return compiledClass.getContainingFile().getVirtualFile();
          }
        }
        return null;
      }
      String classFileName = className + ".class";
      for (PsiDirectory directory : aPackage.getDirectories()) {
        VirtualFile file = directory.getVirtualFile().findChild(classFileName);
        if (file != null) {
          return file;
        }
      }
      return null;
    }

    private List<Equations> loadEquations(Method method) {
      VirtualFile file = findClassFile(method.internalClassName);
      if (file == null) return Collections.emptyList();
      try {
        Map<EKey, Equations> map =
          ClassDataIndexer.processClass(new ClassReader(file.contentsToByteArray(false)), file.getPresentableUrl());
        Map<Method, List<Equations>> groups = EntryStream.of(map).mapKeys(key -> (Method)key.method).grouping();
        myEquationCache.putAll(groups);
        return groups.getOrDefault(method, Collections.emptyList());
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * IndexedEquationProvider (used normally)
   * All EKey's are hashed after processing in ClassDataIndexer; persistent index is used to store equations
   */
  static class IndexedEquationProvider extends EquationProvider<HMethod> {
    IndexedEquationProvider(Project project) {
      super(project);
    }

    @Override
    public EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest) {
      return key.hashed(messageDigest);
    }

    @Override
    public List<Equations> getEquations(MethodDescriptor method) {
      HMethod key = method.hashed(null);
      return myEquationCache.computeIfAbsent(key, m -> BytecodeAnalysisIndex.getEquations(ProjectScope.getLibrariesScope(myProject), m));
    }
  }
}

class MethodAnnotations {
  // @NotNull keys
  final Set<EKey> notNulls = new HashSet<>(1);
  // @Nullable keys
  final Set<EKey> nullables = new HashSet<>(1);
  // @Contract(pure=true) part of contract
  final Set<EKey> pures = new HashSet<>(1);
  // @Contracts
  final Map<EKey, String> contractsValues = new HashMap<>();
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
