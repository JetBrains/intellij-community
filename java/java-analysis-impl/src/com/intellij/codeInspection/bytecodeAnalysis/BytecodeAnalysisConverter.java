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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

  public static MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("MD5");
  }

  /**
   * Converts an equation over asm keys into equation over small hash keys.
   */
  @NotNull
  static DirectionResultPair convert(@NotNull Equation<Key, Value> equation, @NotNull MessageDigest md) {
    ProgressManager.checkCanceled();

    Result<Key, Value> rhs = equation.rhs;
    HResult result;
    if (rhs instanceof Final) {
      result = new HFinal(((Final<Key, Value>)rhs).value);
    } else {
      Pending<Key, Value> pending = (Pending<Key, Value>)rhs;
      Set<Product<Key, Value>> sumOrigin = pending.sum;
      HComponent[] components = new HComponent[sumOrigin.size()];
      int componentI = 0;
      for (Product<Key, Value> prod : sumOrigin) {
        HKey[] intProd = new HKey[prod.ids.size()];
        int idI = 0;
        for (Key id : prod.ids) {
          intProd[idI] = asmKey(id, md);
          idI++;
        }
        HComponent intIdComponent = new HComponent(prod.value, intProd);
        components[componentI] = intIdComponent;
        componentI++;
      }
      result = new HPending(components);
    }
    return new DirectionResultPair(mkDirectionKey(equation.id.direction), result);
  }

  /**
   * Converts an asm method key to a small hash key (HKey)
   */
  @NotNull
  public static HKey asmKey(@NotNull Key key, @NotNull MessageDigest md) {
    byte[] classDigest = md.digest(key.method.internalClassName.getBytes());
    md.update(key.method.methodName.getBytes());
    md.update(key.method.methodDesc.getBytes());
    byte[] sigDigest = md.digest();
    byte[] digest = new byte[HASH_SIZE];
    System.arraycopy(classDigest, 0, digest, 0, CLASS_HASH_SIZE);
    System.arraycopy(sigDigest, 0, digest, CLASS_HASH_SIZE, SIGNATURE_HASH_SIZE);
    return new HKey(digest, mkDirectionKey(key.direction), key.stable);
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
    return new HKey(digest, mkDirectionKey(direction), true);
  }

  @Nullable
  private static byte[] psiClassDigest(@NotNull PsiClass psiClass, @NotNull MessageDigest md) {
    String descriptor = descriptor(psiClass, 0, false);
    if (descriptor == null) {
      return null;
    }
    return md.digest(descriptor.getBytes());
  }

  @Nullable
  private static byte[] methodDigest(@NotNull PsiMethod psiMethod, @NotNull MessageDigest md) {
    String descriptor = descriptor(psiMethod);
    if (descriptor == null) {
      return null;
    }
    return md.digest(descriptor.getBytes());
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

  private static int mkDirectionKey(Direction dir) {
    if (dir instanceof Out) {
      return 0;
    } else if (dir instanceof In) {
      In in = (In)dir;
      // nullity mask is 0/1
      return 8 * in.paramId() + 1 + in.nullityMask;
    } else {
      InOut inOut = (InOut)dir;
      return 8 * inOut.paramId() + 3 + inOut.valueId();
    }
  }

  @NotNull
  private static Direction extractDirection(int directionKey) {
    if (directionKey == 0) {
      return new Out();
    }
    else {
      int paramId = directionKey / 8;
      int subDirection = directionKey % 8;
      if (subDirection <= 2) {
        return new In(paramId, subDirection - 1);
      }
      else {
        return new InOut(paramId, Value.values()[subDirection - 3]);
      }
    }
  }

  /**
   * Given a PSI method and its primary HKey enumerate all contract keys for it.
   */
  @NotNull
  public static ArrayList<HKey> mkInOutKeys(@NotNull PsiMethod psiMethod, @NotNull HKey primaryKey) {
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    ArrayList<HKey> keys = new ArrayList<HKey>(parameters.length * 2 + 1);
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiType parameterType = parameter.getType();
      if (parameterType instanceof PsiPrimitiveType) {
        if (PsiType.BOOLEAN.equals(parameterType)) {
          keys.add(primaryKey.updateDirection(mkDirectionKey(new InOut(i, Value.False))));
          keys.add(primaryKey.updateDirection(mkDirectionKey(new InOut(i, Value.True))));
        }
      } else {
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
   * @param methodKey a primary key of a method being analyzed
   * @param arity arity of this method (hint for constructing @Contract annotations)
   */
  public static void addMethodAnnotations(@NotNull HashMap<HKey, Value> solution, @NotNull MethodAnnotations methodAnnotations, @NotNull HKey methodKey, int arity) {
    List<String> clauses = new ArrayList<String>();
    HashSet<HKey> notNulls = methodAnnotations.notNulls;
    HashMap<HKey, String> contracts = methodAnnotations.contracts;
    for (Map.Entry<HKey, Value> entry : solution.entrySet()) {
      HKey key = entry.getKey().mkStable();
      Value value = entry.getValue();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }
      Direction direction = extractDirection(key.dirKey);
      if (value == Value.NotNull && direction instanceof Out && methodKey.equals(key)) {
        notNulls.add(key);
      }
      else if (direction instanceof InOut) {
        HKey baseKey = key.mkBase();
        if (methodKey.equals(baseKey)) {
          clauses.add(contractElement(arity, (InOut)direction, value));
        }
      }
    }

    if (!notNulls.contains(methodKey) && !clauses.isEmpty()) {
      Collections.sort(clauses);
      StringBuilder sb = new StringBuilder("\"");
      StringUtil.join(clauses, ";", sb);
      sb.append('"');
      contracts.put(methodKey, sb.toString().intern());
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
