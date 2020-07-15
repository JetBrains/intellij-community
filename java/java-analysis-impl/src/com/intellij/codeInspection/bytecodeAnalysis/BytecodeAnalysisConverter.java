// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InOut;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InThrow;
import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public final class BytecodeAnalysisConverter {

  /**
   * Creates a stable non-negated EKey for given PsiMethod and direction
   * Returns null if conversion is impossible (something is not resolvable).
   */
  @Nullable
  public static EKey psiKey(@NotNull PsiMember psiMethod, @NotNull Direction direction) {
    PsiClass psiClass = psiMethod.getContainingClass();
    if (psiClass != null) {
      String className = descriptor(psiClass, 0, false);
      String name = psiMethod.getName();
      String sig;
      if (psiMethod instanceof PsiMethod) {
        sig = methodSignature((PsiMethod)psiMethod, psiClass);
        if (((PsiMethod)psiMethod).isConstructor()) {
          name = "<init>";
        }
      } else if (psiMethod instanceof PsiField) {
        sig = descriptor(((PsiField)psiMethod).getType());
      } else return null;
      if (className != null && sig != null && name != null) {
        return new EKey(new Member(className, name, sig), direction, true, false);
      }
    }
    return null;
  }

  @Nullable
  private static String methodSignature(@NotNull PsiMethod psiMethod, @NotNull PsiClass psiClass) {
    StringBuilder sb = new StringBuilder();

    sb.append('(');
    if (psiMethod.isConstructor() && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass outerClass = psiClass.getContainingClass();
      if (outerClass != null) {
        String desc = descriptor(outerClass, 0, true);
        if (desc == null) return null;
        sb.append(desc);
      }
    }
    for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
      String desc = descriptor(parameter.getType());
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }
    sb.append(')');

    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      sb.append('V');
    }
    else {
      String desc = descriptor(returnType);
      if (desc == null) return null;
      sb.append(desc);
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
      if (qname.length() < packageName.length() + 1 || !qname.startsWith(packageName)) {
        LOG.error("Invalid qname/packageName; qname = "+qname+"; packageName = "+packageName+"; getClass = "+psiClass.getClass().getName());
        return null;
      }
      className = qname.substring(packageName.length() + 1).replace('.', '$');
    }
    else {
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
   * @param psiMethod  psi method
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
      }
      else if (PsiType.BOOLEAN.equals(parameters[i].getType())) {
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

      MutationSignature sig = MutationSignature.pure();
      for (EffectQuantum effect : effects) {
        if (effect.equals(EffectQuantum.ThisChangeQuantum)) {
          // Pure constructor is allowed to change "this" object as this is a new object anyways
          if (!constructor) {
            sig = sig.alsoMutatesThis();
          }
        }
        else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
          int paramN = ((EffectQuantum.ParamChangeQuantum)effect).n;
          sig = sig.alsoMutatesArg(paramN);
        }
        else {
          sig = MutationSignature.unknown();
          break;
        }
      }
      result.mutates.put(methodKey, sig);
    }
  }
}