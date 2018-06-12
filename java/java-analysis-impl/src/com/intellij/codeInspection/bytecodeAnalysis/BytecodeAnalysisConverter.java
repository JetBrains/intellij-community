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

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InOut;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InThrow;
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
    return new EKey(new Member(className, methodName, methodSig), direction, true, false);
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

  public static void addEffectAnnotations(Map<EKey, Effects> puritySolutions,
                                          MethodAnnotations result,
                                          EKey methodKey,
                                          boolean constructor) {
    for (Map.Entry<EKey, Effects> entry : puritySolutions.entrySet()) {
      EKey key = entry.getKey().mkStable();
      EKey baseKey = key.mkBase();
      if (!methodKey.equals(baseKey)) {
        continue;
      }
      result.returnValue = entry.getValue().returnValue;
      Set<EffectQuantum> effects = entry.getValue().effects;
      if (effects.isEmpty() || (constructor && effects.size() == 1 && effects.contains(EffectQuantum.ThisChangeQuantum))) {
        // Pure constructor is allowed to change "this" object as this is a new object anyways
        result.pures.add(methodKey);
      }
    }
  }
}
