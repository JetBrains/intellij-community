// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
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
      String className = getJvmClassName(psiClass);
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
        String desc = descriptor(outerClass, 0);
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
  private static String descriptor(@NotNull PsiClass psiClass, int dimensions) {
    String fqn = getJvmClassName(psiClass);
    if (fqn == null) return null;
    return "[".repeat(dimensions) + 'L' + fqn + ';';
  }

  @Nullable
  private static String getJvmClassName(@NotNull PsiClass psiClass) {
    String name = ClassUtil.getJVMClassName(psiClass);
    return name == null ? null : name.replace('.', '/');
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
        return descriptor(psiClass, dimensions);
      }
      else {
        LOG.debug("resolve was null for " + psiType.getCanonicalText());
        return null;
      }
    }
    else if (TypeConversionUtil.isPrimitiveAndNotNull(psiType)) {
      return "[".repeat(dimensions) + ((PsiPrimitiveType)psiType).getKind().getBinaryName();
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
                                          EKey pureKey,
                                          boolean constructor) {
    Effects solution = puritySolutions.get(pureKey.mkUnstable());
    if (solution == null) {
      solution = puritySolutions.get(pureKey.mkStable());
      if (solution == null) return;
    }
    result.returnValue = solution.returnValue;
    Set<EffectQuantum> effects = solution.effects;

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
    result.mutates = sig;
  }
}