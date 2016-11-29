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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
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

  // how many bytes are taken from class fqn digest
  public static final int CLASS_HASH_SIZE = 10;
  // how many bytes are taken from signature digest
  public static final int SIGNATURE_HASH_SIZE = 4;
  public static final int HASH_SIZE = CLASS_HASH_SIZE + SIGNATURE_HASH_SIZE;

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

  public static MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
    return HASHER_CACHE.getValue();
  }

  /**
   * Converts an equation over asm keys into equation over small hash keys.
   */
  @NotNull
  static DirectionResultPair convert(@NotNull Equation equation, @NotNull MessageDigest md) {
    ProgressManager.checkCanceled();

    Result rhs = equation.rhs;
    HResult hResult;
    if (rhs instanceof Final) {
      hResult = new HFinal(((Final)rhs).value);
    }
    else if (rhs instanceof Pending) {
      Pending pending = (Pending)rhs;
      Set<Product> sumOrigin = pending.sum;
      HComponent[] components = new HComponent[sumOrigin.size()];
      int componentI = 0;
      for (Product prod : sumOrigin) {
        HKey[] intProd = new HKey[prod.ids.size()];
        int idI = 0;
        for (Key key : prod.ids) {
          intProd[idI] = asmKey(key, md);
          idI++;
        }
        HComponent intIdComponent = new HComponent(prod.value, intProd);
        components[componentI] = intIdComponent;
        componentI++;
      }
      hResult = new HPending(components);
    } else {
      Effects wrapper = (Effects)rhs;
      Set<EffectQuantum> effects = wrapper.effects;
      Set<HEffectQuantum> hEffects = new HashSet<>();
      for (EffectQuantum effect : effects) {
        if (effect == EffectQuantum.TopEffectQuantum) {
          hEffects.add(HEffectQuantum.TopEffectQuantum);
        }
        else if (effect == EffectQuantum.ThisChangeQuantum) {
          hEffects.add(HEffectQuantum.ThisChangeQuantum);
        }
        else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
          EffectQuantum.ParamChangeQuantum paramChangeQuantum = (EffectQuantum.ParamChangeQuantum)effect;
          hEffects.add(new HEffectQuantum.ParamChangeQuantum(paramChangeQuantum.n));
        }
        else if (effect instanceof EffectQuantum.CallQuantum) {
          EffectQuantum.CallQuantum callQuantum = (EffectQuantum.CallQuantum)effect;
          hEffects.add(new HEffectQuantum.CallQuantum(asmKey(callQuantum.key, md), callQuantum.data, callQuantum.isStatic));
        }
      }
      hResult = new HEffects(hEffects);
    }
    return new DirectionResultPair(mkDirectionKey(equation.id.direction), hResult);
  }

  /**
   * Converts an asm method key to a small hash key (HKey)
   */
  @NotNull
  public static HKey asmKey(@NotNull Key key, @NotNull MessageDigest md) {
    byte[] classDigest = md.digest(key.method.internalClassName.getBytes(CharsetToolkit.UTF8_CHARSET));
    md.update(key.method.methodName.getBytes(CharsetToolkit.UTF8_CHARSET));
    md.update(key.method.methodDesc.getBytes(CharsetToolkit.UTF8_CHARSET));
    byte[] sigDigest = md.digest();
    byte[] digest = new byte[HASH_SIZE];
    System.arraycopy(classDigest, 0, digest, 0, CLASS_HASH_SIZE);
    System.arraycopy(sigDigest, 0, digest, CLASS_HASH_SIZE, SIGNATURE_HASH_SIZE);
    return new HKey(digest, mkDirectionKey(key.direction), key.stable, key.negated);
  }

  /**
   * Converts a Psi method to a small hash key (HKey).
   * Returns null if conversion is impossible (something is not resolvable).
   */
  @Nullable
  public static HKey psiKey(@NotNull PsiMethod psiMethod, @NotNull Direction direction, @NotNull MessageDigest md) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class, false);
    if (psiClass == null) {
      return null;
    }
    byte[] classDigest = psiClassDigest(psiClass, md);
    if (classDigest == null) {
      return null;
    }
    byte[] sigDigest = methodDigest(psiMethod, md);
    if (sigDigest == null) {
      return null;
    }
    byte[] digest = new byte[HASH_SIZE];
    System.arraycopy(classDigest, 0, digest, 0, CLASS_HASH_SIZE);
    System.arraycopy(sigDigest, 0, digest, CLASS_HASH_SIZE, SIGNATURE_HASH_SIZE);
    return new HKey(digest, mkDirectionKey(direction), true, false);
  }

  @Nullable
  private static byte[] psiClassDigest(@NotNull PsiClass psiClass, @NotNull MessageDigest md) {
    String descriptor = descriptor(psiClass, 0, false);
    if (descriptor == null) {
      return null;
    }
    return md.digest(descriptor.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  @Nullable
  private static byte[] methodDigest(@NotNull PsiMethod psiMethod, @NotNull MessageDigest md) {
    String descriptor = descriptor(psiMethod);
    if (descriptor == null) {
      return null;
    }
    return md.digest(descriptor.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  @Nullable
  private static String descriptor(@NotNull PsiMethod psiMethod) {
    StringBuilder sb = new StringBuilder();
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class, false);
    if (psiClass == null) {
      return null;
    }
    PsiClass outerClass = psiClass.getContainingClass();
    boolean isInnerClassConstructor = psiMethod.isConstructor() && (outerClass != null) && !psiClass.hasModifierProperty(PsiModifier.STATIC);
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    PsiType returnType = psiMethod.getReturnType();

    sb.append(returnType == null ? "<init>" : psiMethod.getName());
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
        LOG.debug("resolve was null for " + ((PsiClassType)psiType).getClassName());
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
   * Converts Direction object to int.
   *
   * 0 - Out
   * 1 - NullableOut
   * 2 - Pure
   *
   * 3 - 0-th NOT_NULL
   * 4 - 0-th NULLABLE
   * ...
   *
   * 11 - 1-st NOT_NULL
   * 12 - 1-st NULLABLE
   *
   * @param dir direction of analysis
   * @return unique int for direction
   */
  static int mkDirectionKey(Direction dir) {
    if (dir == Out) {
      return 0;
    }
    else if (dir == NullableOut) {
      return 1;
    }
    else if (dir == Pure) {
      return 2;
    }
    else if (dir instanceof In) {
      In in = (In)dir;
      // nullity mask is 0/1
      return 3 + 8 * in.paramId() + in.nullityMask;
    }
    else {
      // valueId is [1-5]
      InOut inOut = (InOut)dir;
      return 3 + 8 * inOut.paramId() + 2 + inOut.valueId();
    }
  }

  /**
   * Converts int to Direction object.
   *
   * @param  directionKey int representation of direction
   * @return Direction object
   * @see    #mkDirectionKey(Direction)
   */
  @NotNull
  static Direction extractDirection(int directionKey) {
    if (directionKey == 0) {
      return Out;
    }
    else if (directionKey == 1) {
      return NullableOut;
    }
    else if (directionKey == 2) {
      return Pure;
    }
    else {
      int paramKey = directionKey - 3;
      int paramId = paramKey / 8;
      // shifting first 3 values - now we have key [0 - 7]
      int subDirectionId = paramKey % 8;
      // 0 - 1 - @NotNull, @Nullable, parameter
      if (subDirectionId <= 1) {
        return new In(paramId, subDirectionId);
      }
      else {
        int valueId = subDirectionId - 2;
        return new InOut(paramId, Value.values()[valueId]);
      }
    }
  }

  /**
   * Given a PSI method and its primary HKey enumerate all contract keys for it.
   *
   * @param psiMethod psi method
   * @param primaryKey primary stable keys
   * @return corresponding (stable!) keys
   */
  @NotNull
  public static ArrayList<HKey> mkInOutKeys(@NotNull PsiMethod psiMethod, @NotNull HKey primaryKey) {
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    ArrayList<HKey> keys = new ArrayList<>(parameters.length * 2 + 2);
    keys.add(primaryKey);
    for (int i = 0; i < parameters.length; i++) {
      if (!(parameters[i].getType() instanceof PsiPrimitiveType)) {
        keys.add(primaryKey.updateDirection(mkDirectionKey(new InOut(i, Value.NotNull))));
        keys.add(primaryKey.updateDirection(mkDirectionKey(new InOut(i, Value.Null))));
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
  public static void addMethodAnnotations(@NotNull Map<HKey, Value> solution, @NotNull MethodAnnotations methodAnnotations, @NotNull HKey methodKey, int arity) {
    List<String> contractClauses = new ArrayList<>(arity * 2);
    Set<HKey> notNulls = methodAnnotations.notNulls;
    Set<HKey> pures = methodAnnotations.pures;
    Map<HKey, String> contracts = methodAnnotations.contractsValues;

    for (Map.Entry<HKey, Value> entry : solution.entrySet()) {
      // NB: keys from Psi are always stable, so we need to stabilize keys from equations
      Value value = entry.getValue();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }
      HKey key = entry.getKey().mkStable();
      Direction direction = extractDirection(key.dirKey);
      HKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      if (value == Value.NotNull && direction == Out) {
        notNulls.add(methodKey);
      }
      else if (value == Value.Pure && direction == Pure) {
        pures.add(methodKey);
      }
      else if (direction instanceof InOut) {
        contractClauses.add(contractElement(arity, (InOut)direction, value));
      }
    }

    if (!notNulls.contains(methodKey) && !contractClauses.isEmpty()) {
      // no contract clauses for @NotNull methods
      Collections.sort(contractClauses);
      StringBuilder sb = new StringBuilder("\"");
      StringUtil.join(contractClauses, ";", sb);
      sb.append('"');
      contracts.put(methodKey, sb.toString().intern());
    }

  }

  public static void addEffectAnnotations(Map<HKey, Set<HEffectQuantum>> puritySolutions, MethodAnnotations result, HKey methodKey, int arity) {
    for (Map.Entry<HKey, Set<HEffectQuantum>> entry : puritySolutions.entrySet()) {
      Set<HEffectQuantum> effects = entry.getValue();
      HKey key = entry.getKey().mkStable();
      HKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      if (effects.isEmpty()) {
        result.pures.add(methodKey);
      }
    }
  }

  private static String contractValueString(@NotNull Value v) {
    switch (v) {
      case False: return "false";
      case True: return "true";
      case NotNull: return "!null";
      case Null: return "null";
      default: return "_";
    }
  }

  private static String contractElement(int arity, InOut inOut, Value value) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < arity; i++) {
      Value currentValue = Value.Top;
      if (i == inOut.paramIndex) {
        currentValue = inOut.inValue;
      }
      if (i > 0) {
        sb.append(',');
      }
      sb.append(contractValueString(currentValue));
    }
    sb.append("->");
    sb.append(contractValueString(value));
    return sb.toString();
  }

}
