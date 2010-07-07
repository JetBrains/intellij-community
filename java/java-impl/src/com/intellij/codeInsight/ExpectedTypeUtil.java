/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExpectedTypeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypeUtil");

  public static ExpectedTypeInfo[] intersect(List<ExpectedTypeInfo[]> typeInfos) {
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

    public ExpectedTypeInfos() {
      myInfos = new ArrayList<ExpectedTypeInfo>();
    }

    public ExpectedTypeInfos(ExpectedTypeInfo[] infos) {
      myInfos = new ArrayList<ExpectedTypeInfo>(Arrays.asList(infos));
    }

    public void clear () { myInfos.clear(); }

    public void addInfo (ExpectedTypeInfo info) {
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

    public ExpectedTypeInfo[] toArray() {
      return myInfos.toArray(new ExpectedTypeInfo[myInfos.size()]);
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
    } else if (kind1 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info1.getType(), info2) ? -1 : 0;
    } else if (kind2 == ExpectedTypeInfo.TYPE_STRICTLY) {
      return matches(info2.getType(), info1) ? 1  : 0;
    }
    return 0;
  }

  private static boolean matchesStrictly (PsiType type, ExpectedTypeInfo info) {
    if ((type instanceof PsiPrimitiveType) != (info.getType() instanceof PsiPrimitiveType)) return false;
    return matches(type, info);
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
    }

    LOG.error("Unexpected ExpectedInfo kind");
    return false;
  }

  public static class ExpectedClassesFromSetProvider implements ExpectedTypesProvider.ExpectedClassProvider {
    private final Set<PsiClass> myOccurrenceClasses;

    public ExpectedClassesFromSetProvider(Set<PsiClass> occurrenceClasses) {
      myOccurrenceClasses = occurrenceClasses;
    }

    public PsiField[] findDeclaredFields(final PsiManager manager, String name) {
      List<PsiField> fields = new ArrayList<PsiField>();
      for (PsiClass aClass : myOccurrenceClasses) {
        final PsiField field = aClass.findFieldByName(name, true);
        if (field != null) fields.add(field);
      }
      return fields.toArray(new PsiField[fields.size()]);
    }

    public PsiMethod[] findDeclaredMethods(final PsiManager manager, String name) {
      List<PsiMethod> methods = new ArrayList<PsiMethod>();
      for (PsiClass aClass : myOccurrenceClasses) {
        final PsiMethod[] occMethod = aClass.findMethodsByName(name, true);
        ContainerUtil.addAll(methods, occMethod);
      }
      return methods.toArray(new PsiMethod[methods.size()]);
    }
  }

  @Nullable
  public static PsiSubstitutor inferSubstitutor(final PsiMethod method, final PsiMethodCallExpression callExpr, final boolean forCompletion) {
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] args = callExpr.getArgumentList().getExpressions();
    PsiSubstitutor result = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(method.getContainingClass())) {
      PsiType type = helper.inferTypeForMethodTypeParameter(typeParameter, parameters, args, PsiSubstitutor.EMPTY, callExpr.getParent(), forCompletion);
      if (PsiType.NULL.equals(type)) return null;
      result = result.put(typeParameter, type);
    }

    return result;
  }

}
