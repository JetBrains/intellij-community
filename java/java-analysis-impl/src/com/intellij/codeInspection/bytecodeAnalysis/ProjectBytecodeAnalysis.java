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
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
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
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

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
    if (annotationFQN.equals(AnnotationUtil.NOT_NULL) || annotationFQN.equals(AnnotationUtil.NULLABLE) || annotationFQN.equals(
      JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
      PsiAnnotation[] annotations = findInferredAnnotations(listOwner);
      for (PsiAnnotation annotation : annotations) {
        if (annotationFQN.equals(annotation.getQualifiedName())) {
          return annotation;
        }
      }
    }
    return null;
  }

  @NotNull
  public PsiAnnotation[] findInferredAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    return CachedValuesManager.getCachedValue(listOwner,
                                              () -> CachedValueProvider.Result.create(collectInferredAnnotations(listOwner), listOwner));
  }

  @NotNull
  private PsiAnnotation[] collectInferredAnnotations(PsiModifierListOwner listOwner) {
    PsiFile psiFile = listOwner.getContainingFile();
    VirtualFile file = psiFile == null ? null : psiFile.getVirtualFile();
    if (file != null && ClassDataIndexer.isFileExcluded(file)) return PsiAnnotation.EMPTY_ARRAY;

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
        ConcurrentFactoryMap.createMap(attrs -> createAnnotationFromText("@org.jetbrains.annotations.Contract(" + attrs + ")"));
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
    final EKey nullableKey = new EKey(notNullKey.member, notNullKey.dirKey + 1, true, false);
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

    int arity = owner.getParameterList().getParametersCount();
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
      addMethodAnnotations(solutions, result, key, arity);
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
    return new EKey(key.member, key.dirKey, stability, false);
  }

  private void collectPurityEquations(EKey key, PuritySolver puritySolver)
    throws EquationsLimitException {
    HashSet<EKey> queued = new HashSet<>();
    ArrayDeque<EKey> queue = new ArrayDeque<>();

    queue.push(key);
    queued.add(key);

    while (!queue.isEmpty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      EKey curKey = queue.pop();

      boolean stable = true;
      Effects combined = null;
      for (Equations equations : myEquationProvider.getEquations(curKey.member)) {
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
    puritySolver.addPlainFieldEquations(md -> true);
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

      for (Equations equations : myEquationProvider.getEquations(curKey.member)) {
        Result result = equations.find(curKey.getDirection()).orElseGet(solver::getUnknownResult);
        solver.addEquation(new Equation(withStability(curKey, equations.stable), result));
        result.dependencies().filter(queued::add).forEach(queue::push);
      }
    }
  }

  private void collectSingleEquation(EKey curKey, Solver solver) {
    ProgressManager.checkCanceled();

    for (Equations equations : myEquationProvider.getEquations(curKey.member)) {
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

  BitSet findAlwaysNotNullParameters(@NotNull EKey methodKey, BitSet possiblyNotNullParameters) throws EquationsLimitException {
    BitSet alwaysNotNullParameters = new BitSet();
    if (possiblyNotNullParameters.cardinality() != 0) {
      List<EKey> keys = IntStreamEx.of(possiblyNotNullParameters).mapToObj(idx -> methodKey.withDirection(new In(idx, false))).toList();
      final Solver notNullSolver = new Solver(new ELattice<>(Value.NotNull, Value.Top), Value.Top);
      collectEquations(keys, notNullSolver);

      Map<EKey, Value> notNullSolutions = notNullSolver.solve();
      alwaysNotNullParameters = IntStreamEx.of(possiblyNotNullParameters).filter(idx -> {
        EKey key = methodKey.withDirection(new In(idx, false));
        return notNullSolutions.get(key) == Value.NotNull || notNullSolutions.get(key.mkUnstable()) == Value.NotNull;
      }).toBitSet();
    }
    return alwaysNotNullParameters;
  }

  /**
   * Given `solution` of all dependencies of a method with the `methodKey`, converts this solution into annotations.
   *
   * @param solution solution of equations
   * @param methodAnnotations annotations to which corresponding solutions should be added
   * @param methodKey a primary key of a method being analyzed. not it is stable
   * @param arity arity of this method (hint for constructing @Contract annotations)
   */
  private void addMethodAnnotations(@NotNull Map<EKey, Value> solution, @NotNull MethodAnnotations methodAnnotations, @NotNull EKey methodKey, int arity)
    throws EquationsLimitException {
    List<StandardMethodContract> contractClauses = new ArrayList<>();
    Set<EKey> notNulls = methodAnnotations.notNulls;
    Set<EKey> pures = methodAnnotations.pures;
    Map<EKey, String> contracts = methodAnnotations.contractsValues;

    ContractReturnValue fullReturnValue = methodAnnotations.returnValue.asContractReturnValue();
    for (Map.Entry<EKey, Value> entry : solution.entrySet()) {
      // NB: keys from Psi are always stable, so we need to stabilize keys from equations
      Value value = entry.getValue();
      if (value == Value.Top || value == Value.Bot || (value == Value.Fail && !pures.contains(methodKey))) {
        continue;
      }
      EKey key = entry.getKey().mkStable();
      Direction direction = key.getDirection();
      EKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      if (value == Value.NotNull && direction == Out) {
        notNulls.add(methodKey);
      }
      else if (value == Value.Pure && direction == Pure) {
        pures.add(methodKey);
      }
      else if (direction instanceof ParamValueBasedDirection) {
        ContractReturnValue contractReturnValue =
          fullReturnValue.equals(ContractReturnValue.returnAny()) ? value.toReturnValue() : fullReturnValue;
        contractClauses.add(contractElement(arity, (ParamValueBasedDirection)direction, contractReturnValue));
      }
    }

    Map<Boolean, List<StandardMethodContract>> partition =
      StreamEx.of(contractClauses).partitioningBy(c -> c.getReturnValue().isFail());
    List<StandardMethodContract> failingContracts = squashContracts(partition.get(true));
    List<StandardMethodContract> nonFailingContracts = squashContracts(partition.get(false));
    // Sometimes "null,_->!null;!null,_->!null" contracts are inferred for some reason
    // They are squashed to "_,_->!null" which is better expressed as @NotNull annotation
    if (nonFailingContracts.size() == 1) {
      StandardMethodContract contract = nonFailingContracts.get(0);
      if (contract.getReturnValue().isNotNull() && contract.isTrivial()) {
        nonFailingContracts = Collections.emptyList();
        notNulls.add(methodKey);
      }
    }
    List<StandardMethodContract> allContracts = StreamEx.of(failingContracts, nonFailingContracts).toFlatList(Function.identity());
    removeConstraintFromNonNullParameter(methodKey, allContracts);

    if (allContracts.isEmpty() && !fullReturnValue.equals(ContractReturnValue.returnAny())) {
      allContracts.add(StandardMethodContract.trivialContract(arity, fullReturnValue));
    }
    if (notNulls.contains(methodKey)) {
      // filter contract clauses for @NotNull methods
      allContracts.removeIf(smc -> smc.getReturnValue().equals(ContractReturnValue.returnNotNull()));
    }
    // Failing contracts go first
    String result = StreamEx.of(allContracts)
                            .sorted(Comparator.comparingInt((StandardMethodContract smc) -> smc.getReturnValue().isFail() ? 0 : 1)
                                              .thenComparing(StandardMethodContract::toString))
                            .map(Object::toString)
                            .distinct()
                            .map(str -> str.replace(" ", "")) // for compatibility with existing tests
                            .joining(";");
    if (!result.isEmpty()) {
      contracts.put(methodKey, '"' + result + '"');
    }
  }

  private void removeConstraintFromNonNullParameter(@NotNull EKey methodKey,
                                                    List<StandardMethodContract> allContracts) throws EquationsLimitException {
    BitSet possiblyNotNullParameters = StreamEx.of(allContracts)
                                               .flatMapToInt(
                                                 smc -> IntStreamEx.range(smc.getParameterCount())
                                                                   .filter(idx -> smc.getParameterConstraint(idx) == ValueConstraint.NOT_NULL_VALUE))
                                               .toBitSet();
    BitSet alwaysNotNullParameters = findAlwaysNotNullParameters(methodKey, possiblyNotNullParameters);
    if (alwaysNotNullParameters.cardinality() != 0) {
      allContracts.replaceAll(smc -> {
        ValueConstraint[] constraints = smc.getConstraints().toArray(new ValueConstraint[0]);
        alwaysNotNullParameters.stream().forEach(idx -> constraints[idx] = ValueConstraint.ANY_VALUE);
        return new StandardMethodContract(constraints, smc.getReturnValue());
      });
    }
  }

  @NotNull
  private static List<StandardMethodContract> squashContracts(List<StandardMethodContract> contractClauses) {
    // If there's a pair of contracts yielding the same value like "null,_->true", "!null,_->true"
    // then trivial contract should be used like "_,_->true"
    StandardMethodContract soleContract = StreamEx.ofPairs(contractClauses, (c1, c2) -> {
      if (c1.getReturnValue() != c2.getReturnValue()) return null;
      int idx = -1;
      for (int i = 0; i < c1.getParameterCount(); i++) {
        ValueConstraint left = c1.getParameterConstraint(i);
        ValueConstraint right = c2.getParameterConstraint(i);
        if (left == ValueConstraint.ANY_VALUE && right == ValueConstraint.ANY_VALUE) continue;
        if (idx >= 0 || !right.canBeNegated() || left != right.negate()) return null;
        idx = i;
      }
      return c1;
    }).nonNull().findFirst().orElse(null);
    if(soleContract != null) {
      contractClauses =
        Collections.singletonList(StandardMethodContract.trivialContract(soleContract.getParameterCount(), soleContract.getReturnValue()));
    }
    return contractClauses;
  }

  private static StandardMethodContract contractElement(int arity,
                                                        ParamValueBasedDirection inOut, ContractReturnValue returnValue) {
    final ValueConstraint[] constraints = new ValueConstraint[arity];
    Arrays.fill(constraints, ValueConstraint.ANY_VALUE);
    constraints[inOut.paramIndex] = inOut.inValue.toValueConstraint();
    return new StandardMethodContract(constraints, returnValue);
  }

  static abstract class EquationProvider<T extends MemberDescriptor> {
    final Map<T, List<Equations>> myEquationCache = ContainerUtil.createConcurrentSoftValueMap();
    final Project myProject;

    EquationProvider(Project project) {
      myProject = project;
      project.getMessageBus().connect().subscribe(PsiModificationTracker.TOPIC, myEquationCache::clear);
    }

    abstract EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest);

    abstract List<Equations> getEquations(MemberDescriptor method);
  }

  /**
   * PlainEquationProvider (used for debug purposes)
   * All EKey's are not hashed; persistent index is not used to store equations
   */
  static class PlainEquationProvider extends EquationProvider<Member> {
    PlainEquationProvider(Project project) {
      super(project);
    }

    @Override
    public EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest) {
      assert key.member instanceof Member;
      return key;
    }

    @Override
    public List<Equations> getEquations(MemberDescriptor memberDescriptor) {
      assert memberDescriptor instanceof Member;
      Member method = (Member)memberDescriptor;
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
        if (file != null && !ClassDataIndexer.isFileExcluded(file)) {
          return file;
        }
      }
      return null;
    }

    private List<Equations> loadEquations(Member method) {
      VirtualFile file = findClassFile(method.internalClassName);
      if (file == null) return Collections.emptyList();
      try {
        Map<EKey, Equations> map =
          ClassDataIndexer.processClass(new ClassReader(file.contentsToByteArray(false)), file.getPresentableUrl());
        Map<Member, List<Equations>> groups = EntryStream.of(map).mapKeys(key -> (Member)key.member).grouping();
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
  static class IndexedEquationProvider extends EquationProvider<HMember> {
    IndexedEquationProvider(Project project) {
      super(project);
    }

    @Override
    public EKey adaptKey(@NotNull EKey key, MessageDigest messageDigest) {
      return key.hashed(messageDigest);
    }

    @Override
    public List<Equations> getEquations(MemberDescriptor method) {
      HMember key = method.hashed(null);
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
  DataValue returnValue = DataValue.UnknownDataValue1;
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
