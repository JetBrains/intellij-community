// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.CompletionParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ExpectedTypeUtil {
  private static final Logger LOG = Logger.getInstance(ExpectedTypeUtil.class);

  public static ExpectedTypeInfo @NotNull [] intersect(List<ExpectedTypeInfo[]> typeInfos) {
    if (typeInfos.isEmpty()) return ExpectedTypeInfo.EMPTY_ARRAY;

    ExpectedTypeInfos result = new ExpectedTypeInfos(typeInfos.get(0));
    ExpectedTypeInfos acc = new ExpectedTypeInfos();

    for (int i = 1; i < typeInfos.size(); i++) {
      ExpectedTypeInfo[] next = typeInfos.get(i);
      acc.clear();
      for (ExpectedTypeInfo info : next) {
        for (Iterator<ExpectedTypeInfo> iterator = result.iterator(); iterator.hasNext();) {
          ExpectedTypeInfo[] intersection = iterator.next().intersect(info);
          for (ExpectedTypeInfo aIntersection : intersection) {
            acc.addInfo(aIntersection);
          }
        }
      }
      if (acc.isEmpty()) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }
      result = new ExpectedTypeInfos(acc.toArray());
    }

    return result.toArray();
  }

  private static class ExpectedTypeInfos {
    List<ExpectedTypeInfo> myInfos;

    ExpectedTypeInfos() {
      myInfos = new ArrayList<>();
    }

    ExpectedTypeInfos(ExpectedTypeInfo[] infos) {
      myInfos = new ArrayList<>(Arrays.asList(infos));
    }

    public void clear () { myInfos.clear(); }

    void addInfo(ExpectedTypeInfo info) {
      for (Iterator<ExpectedTypeInfo> iterator = myInfos.iterator(); iterator.hasNext();) {
        ExpectedTypeInfo sub = iterator.next();
        int cmp = contains(sub, info);
        if (cmp > 0) return;
        else if (cmp < 0) {
          iterator.remove();
        }
      }
      myInfos.add(info);
    }

    public boolean isEmpty() {
      return myInfos.isEmpty();
    }

    public Iterator<ExpectedTypeInfo> iterator() {
      return myInfos.iterator();
    }

    public ExpectedTypeInfo @NotNull [] toArray() {
      return myInfos.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }
  }

  /**
   * @return <0 if info2 contains info1 (or they are equal)
   *         >0 if info1 contains info2
   *          0 otherwise
   */
  public static int contains(ExpectedTypeInfo info1, ExpectedTypeInfo info2) {
    int kind1 = info1.getKind();
    int kind2 = info2.getKind();
    if (kind1 == kind2) {
      if (matchesStrictly(info1.getType(), info2)) return -1;
      if (matchesStrictly(info2.getType(), info1)) return 1;
      return 0;
    }
    if (kind1 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info1.getType(), info2) ? -1 : 0;
    }
    if (kind2 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info2.getType(), info1) ? 1  : 0;
    }
    return 0;
  }

  private static boolean matchesStrictly (PsiType type, ExpectedTypeInfo info) {
    return type instanceof PsiPrimitiveType == info.getType() instanceof PsiPrimitiveType && matches(type, info);
  }

  public static boolean matches (PsiType type, ExpectedTypeInfo info) {
    PsiType infoType = info.getType();
    switch (info.getKind()) {
      case ExpectedTypeInfo.TYPE_STRICTLY:
        return type.equals(infoType);
      case ExpectedTypeInfo.TYPE_OR_SUBTYPE:
        return infoType.isAssignableFrom(type);
      case ExpectedTypeInfo.TYPE_OR_SUPERTYPE:
        return type.isAssignableFrom(infoType);
      case ExpectedTypeInfo.TYPE_BETWEEN:
        return type.isAssignableFrom(info.getDefaultType()) && infoType.isAssignableFrom(type);
      case ExpectedTypeInfo.TYPE_SAME_SHAPED:
        return true;
    }

    LOG.error("Unexpected ExpectedInfo kind");
    return false;
  }

  public static class ExpectedClassesFromSetProvider implements ExpectedTypesProvider.ExpectedClassProvider {
    private final Set<? extends PsiClass> myOccurrenceClasses;

    public ExpectedClassesFromSetProvider(@NotNull Set<? extends PsiClass> occurrenceClasses) {
      myOccurrenceClasses = occurrenceClasses;
    }

    @Override
    public PsiField @NotNull [] findDeclaredFields(@NotNull final PsiManager manager, @NotNull String name) {
      List<PsiField> fields = new ArrayList<>();
      for (PsiClass aClass : myOccurrenceClasses) {
        final PsiField field = aClass.findFieldByName(name, true);
        if (field != null) fields.add(field);
      }
      return fields.toArray(PsiField.EMPTY_ARRAY);
    }

    @Override
    public PsiMethod @NotNull [] findDeclaredMethods(@NotNull final PsiManager manager, @NotNull String name) {
      List<PsiMethod> methods = new ArrayList<>();
      for (PsiClass aClass : myOccurrenceClasses) {
        final PsiMethod[] occMethod = aClass.findMethodsByName(name, true);
        ContainerUtil.addAll(methods, occMethod);
      }
      return methods.toArray(PsiMethod.EMPTY_ARRAY);
    }
  }

  @Nullable
  public static PsiSubstitutor inferSubstitutor(final PsiMethod method, final PsiMethodCallExpression callExpr, boolean forCompletion) {
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] args = callExpr.getArgumentList().getExpressions();
    PsiSubstitutor result = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(method.getContainingClass())) {
      PsiType type = helper.inferTypeForMethodTypeParameter(typeParameter, parameters, args, PsiSubstitutor.EMPTY, callExpr.getParent(),
                                                            forCompletion ? CompletionParameterTypeInferencePolicy.INSTANCE : DefaultParameterTypeInferencePolicy.INSTANCE);
      if (PsiType.NULL.equals(type)) return null;
      result = result.put(typeParameter, type);
    }

    return result;
  }

}
