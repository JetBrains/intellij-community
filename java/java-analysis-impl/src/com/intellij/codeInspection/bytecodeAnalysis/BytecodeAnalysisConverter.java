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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;
import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverter {

  private static final ThreadLocalCachedValue<MessageDigest> HASHER_CACHE = new ThreadLocalCachedValue<MessageDigest>() {
    @Override
    public MessageDigest create() {
      try {
        return MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException exception) {
        throw new RuntimeException(exception);
      }
    }

    @Override
    protected void init(MessageDigest value) {
      value.reset();
    }
  };

  public static MessageDigest getMessageDigest() {
    return HASHER_CACHE.getValue();
  }

  /**
   * Creates a stable non-negated EKey for given PsiMethod and direction
   * Returns null if conversion is impossible (something is not resolvable).
   */
  @Nullable
  public static EKey psiKey(@NotNull PsiMethod psiMethod, @NotNull Direction direction) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (psiClass == null) {
      return null;
    }
    String className = descriptor(psiClass, 0, false);
    String methodSig = methodSignature(psiMethod);
    if (className == null || methodSig == null) {
      return null;
    }
    String methodName = psiMethod.getReturnType() == null ? "<init>" : psiMethod.getName();
    return new EKey(new Method(className, methodName, methodSig), direction, true, false);
  }

  @Nullable
  private static String methodSignature(@NotNull PsiMethod psiMethod) {
    StringBuilder sb = new StringBuilder();
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class, false);
    if (psiClass == null) {
      return null;
    }
    PsiClass outerClass = psiClass.getContainingClass();
    boolean isInnerClassConstructor = psiMethod.isConstructor() && (outerClass != null) && !psiClass.hasModifierProperty(PsiModifier.STATIC);
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    PsiType returnType = psiMethod.getReturnType();

    sb.append('(');

    String desc;

    if (isInnerClassConstructor) {
      desc = descriptor(outerClass, 0, true);
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }
    for (PsiParameter parameter : parameters) {
      desc = descriptor(parameter.getType());
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }
    sb.append(')');
    if (returnType == null) {
      sb.append('V');
    } else {
      desc = descriptor(returnType);
      if (desc == null) {
        return null;
      } else {
        sb.append(desc);
      }
    }
    return sb.toString();
  }

  @Nullable
  private static String descriptor(@NotNull PsiClass psiClass, int dimensions, boolean full) {
    PsiFile containingFile = psiClass.getContainingFile();
    if (!(containingFile instanceof PsiClassOwner)) {
      LOG.debug("containingFile was not resolved for " + psiClass.getQualifiedName());
      return null;
    }
    PsiClassOwner psiFile = (PsiClassOwner)containingFile;
    String packageName = psiFile.getPackageName();
    String qname = psiClass.getQualifiedName();
    if (qname == null) {
      return null;
    }
    String className;
    if (packageName.length() > 0) {
      className = qname.substring(packageName.length() + 1).replace('.', '$');
    } else {
      className = qname.replace('.', '$');
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dimensions; i++) {
      sb.append('[');
    }
    if (full) {
      sb.append('L');
    }
    if (packageName.length() > 0) {
      sb.append(packageName.replace('.', '/'));
      sb.append('/');
    }
    sb.append(className);
    if (full) {
      sb.append(';');
    }
    return sb.toString();
  }

  @Nullable
  private static String descriptor(@NotNull PsiType psiType) {
    int dimensions = 0;
    psiType = TypeConversionUtil.erasure(psiType);
    if (psiType instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType)psiType;
      psiType = arrayType.getDeepComponentType();
      dimensions = arrayType.getArrayDimensions();
    }

    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)psiType).resolve();
      if (psiClass != null) {
        return descriptor(psiClass, dimensions, true);
      }
      else {
        LOG.debug("resolve was null for " + psiType.getCanonicalText());
        return null;
      }
    }
    else if (psiType instanceof PsiPrimitiveType) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < dimensions; i++) {
         sb.append('[');
      }
      if (PsiType.VOID.equals(psiType)) {
        sb.append('V');
      }
      else if (PsiType.BOOLEAN.equals(psiType)) {
        sb.append('Z');
      }
      else if (PsiType.CHAR.equals(psiType)) {
        sb.append('C');
      }
      else if (PsiType.BYTE.equals(psiType)) {
        sb.append('B');
      }
      else if (PsiType.SHORT.equals(psiType)) {
        sb.append('S');
      }
      else if (PsiType.INT.equals(psiType)) {
        sb.append('I');
      }
      else if (PsiType.FLOAT.equals(psiType)) {
        sb.append('F');
      }
      else if (PsiType.LONG.equals(psiType)) {
        sb.append('J');
      }
      else if (PsiType.DOUBLE.equals(psiType)) {
        sb.append('D');
      }
      return sb.toString();
    }
    return null;
  }


  /**
   * Given a PSI method and its primary Key enumerate all contract keys for it.
   *
   * @param psiMethod psi method
   * @param primaryKey primary stable keys
   * @return corresponding (stable!) keys
   */
  @NotNull
  public static ArrayList<EKey> mkInOutKeys(@NotNull PsiMethod psiMethod, @NotNull EKey primaryKey) {
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    ArrayList<EKey> keys = new ArrayList<>(parameters.length * 2 + 2);
    keys.add(primaryKey);
    for (int i = 0; i < parameters.length; i++) {
      if (!(parameters[i].getType() instanceof PsiPrimitiveType)) {
        keys.add(primaryKey.withDirection(new InOut(i, Value.NotNull)));
        keys.add(primaryKey.withDirection(new InOut(i, Value.Null)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.NotNull)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.Null)));
      } else if (PsiType.BOOLEAN.equals(parameters[i].getType())) {
        keys.add(primaryKey.withDirection(new InOut(i, Value.True)));
        keys.add(primaryKey.withDirection(new InOut(i, Value.False)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.True)));
        keys.add(primaryKey.withDirection(new InThrow(i, Value.False)));
      }
    }
    return keys;
  }

  /**
   * Given `solution` of all dependencies of a method with the `methodKey`, converts this solution into annotations.
   *
   * @param solution solution of equations
   * @param methodAnnotations annotations to which corresponding solutions should be added
   * @param methodKey a primary key of a method being analyzed. not it is stable
   * @param arity arity of this method (hint for constructing @Contract annotations)
   */
  public static void addMethodAnnotations(@NotNull Map<EKey, Value> solution, @NotNull MethodAnnotations methodAnnotations, @NotNull EKey methodKey, int arity) {
    List<StandardMethodContract> contractClauses = new ArrayList<>();
    Set<EKey> notNulls = methodAnnotations.notNulls;
    Set<EKey> pures = methodAnnotations.pures;
    Map<EKey, String> contracts = methodAnnotations.contractsValues;

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
        contractClauses.add(contractElement(arity, (ParamValueBasedDirection)direction, value));
      }
    }

    // no contract clauses for @NotNull methods
    if (!notNulls.contains(methodKey) && !contractClauses.isEmpty()) {
      Map<Boolean, List<StandardMethodContract>> partition =
        StreamEx.of(contractClauses).partitioningBy(c -> c.getReturnValue() == ValueConstraint.THROW_EXCEPTION);
      List<StandardMethodContract> failingContracts = squashContracts(partition.get(true));
      List<StandardMethodContract> nonFailingContracts = squashContracts(partition.get(false));
      // Sometimes "null,_->!null;!null,_->!null" contracts are inferred for some reason
      // They are squashed to "_,_->!null" which is better expressed as @NotNull annotation
      if(nonFailingContracts.size() == 1) {
        StandardMethodContract contract = nonFailingContracts.get(0);
        if(contract.getReturnValue() == ValueConstraint.NOT_NULL_VALUE && contract.isTrivial()) {
          nonFailingContracts = Collections.emptyList();
          notNulls.add(methodKey);
        }
      }
      // Failing contracts go first
      String result = StreamEx.of(failingContracts, nonFailingContracts)
        .flatMap(list -> list.stream()
          .map(Object::toString)
          .map(str -> str.replace(" ", "")) // for compatibility with existing tests
          .sorted())
        .joining(";");
      if(!result.isEmpty()) {
        contracts.put(methodKey, '"'+result+'"');
      }
    }

  }

  @NotNull
  private static List<StandardMethodContract> squashContracts(List<StandardMethodContract> contractClauses) {
    // If there's a pair of contracts yielding the same value like "null,_->true", "!null,_->true"
    // then trivial contract should be used like "_,_->true"
    StandardMethodContract soleContract = StreamEx.ofPairs(contractClauses, (c1, c2) -> {
      if (c1.getReturnValue() != c2.getReturnValue()) return null;
      int idx = -1;
      for (int i = 0; i < c1.arguments.length; i++) {
        ValueConstraint left = c1.arguments[i];
        ValueConstraint right = c2.arguments[i];
        if (left == ValueConstraint.ANY_VALUE && right == ValueConstraint.ANY_VALUE) continue;
        if (idx >= 0 || !right.canBeNegated() || left != right.negate()) return null;
        idx = i;
      }
      return c1;
    }).nonNull().findFirst().orElse(null);
    if(soleContract != null) {
      Arrays.fill(soleContract.arguments, ValueConstraint.ANY_VALUE);
      contractClauses = Collections.singletonList(soleContract);
    }
    return contractClauses;
  }

  public static void addEffectAnnotations(Map<EKey, Set<EffectQuantum>> puritySolutions,
                                          MethodAnnotations result,
                                          EKey methodKey,
                                          boolean constructor) {
    for (Map.Entry<EKey, Set<EffectQuantum>> entry : puritySolutions.entrySet()) {
      Set<EffectQuantum> effects = entry.getValue();
      EKey key = entry.getKey().mkStable();
      EKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      if (effects.isEmpty() || (constructor && effects.size() == 1 && effects.contains(EffectQuantum.ThisChangeQuantum))) {
        // Pure constructor is allowed to change "this" object as this is a new object anyways
        result.pures.add(methodKey);
      }
    }
  }

  private static StandardMethodContract contractElement(int arity, ParamValueBasedDirection inOut, Value value) {
    final ValueConstraint[] constraints = new ValueConstraint[arity];
    Arrays.fill(constraints, ValueConstraint.ANY_VALUE);
    constraints[inOut.paramIndex] = inOut.inValue.toValueConstraint();
    return new StandardMethodContract(constraints, value.toValueConstraint());
  }
}
