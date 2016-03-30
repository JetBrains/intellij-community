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
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PsiSuperMethodUtil {
  private PsiSuperMethodUtil() {}

  public static PsiMethod findConstructorInSuper(PsiMethod constructor) {
    return findConstructorInSuper(constructor, new HashSet<PsiMethod>());
  }

  public static PsiMethod findConstructorInSuper(PsiMethod constructor, Set<PsiMethod> visited) {
    if (visited.contains(constructor)) return null;
    visited.add(constructor);
    final PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiElement firstChild = statements[0].getFirstChild();
        if (firstChild instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)firstChild).getMethodExpression();
          @NonNls final String text = methodExpr.getText();
          if (text.equals("super")) {
            PsiElement superConstructor = methodExpr.resolve();
            if (superConstructor instanceof PsiMethod) {
              return (PsiMethod)superConstructor;
            }
          } else if (text.equals("this")) {
            final PsiElement resolved = methodExpr.resolve();
            if (resolved instanceof PsiMethod) {
              return findConstructorInSuper((PsiMethod)resolved, visited);
            }
            return null;
          }
        }
      }
    }

    PsiClass containingClass = constructor.getContainingClass();
    if (containingClass != null) {
      PsiClass superClass = containingClass.getSuperClass();
      if (superClass != null) {
        MethodSignature defConstructor = MethodSignatureUtil.createMethodSignature(superClass.getName(), PsiType.EMPTY_ARRAY,
                                                                                   PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, true);
        return MethodSignatureUtil.findMethodBySignature(superClass, defConstructor, false);
      }
    }
    return null;
  }

  public static boolean isSuperMethod(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
    for (int i = 0, superSignaturesSize = superSignatures.size(); i < superSignaturesSize; i++) {
      HierarchicalMethodSignature supsig = superSignatures.get(i);
      PsiMethod supsigme = supsig.getMethod();
      if (superMethod.equals(supsigme) || isSuperMethod(supsigme, superMethod)) return true;
    }

    return false;
  }

  @NotNull
  public static PsiSubstitutor obtainFinalSubstitutor(@NotNull PsiClass superClass,
                                                      @NotNull PsiSubstitutor superSubstitutor,
                                                      @NotNull PsiSubstitutor derivedSubstitutor,
                                                      boolean inRawContext) {
    if (inRawContext) {
      Set<PsiTypeParameter> typeParams = superSubstitutor.getSubstitutionMap().keySet();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(superClass.getProject());
      superSubstitutor = factory.createRawSubstitutor(derivedSubstitutor, typeParams.toArray(new PsiTypeParameter[typeParams.size()]));
    }
    Map<PsiTypeParameter, PsiType> map = null;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(superClass)) {
      PsiType type = superSubstitutor.substitute(typeParameter);
      final PsiType t = derivedSubstitutor.substitute(type);
      if (map == null) {
        map = new THashMap<PsiTypeParameter, PsiType>();
      }
      map.put(typeParameter, t);
    }

    return map == null ? PsiSubstitutor.EMPTY : JavaPsiFacade.getInstance(superClass.getProject()).getElementFactory().createSubstitutor(map);
  }

  @NotNull
  public static Map<MethodSignature, Set<PsiMethod>> collectOverrideEquivalents(@NotNull PsiClass aClass) {
    final Map<MethodSignature, Set<PsiMethod>> overrideEquivalent =
      new THashMap<MethodSignature, Set<PsiMethod>>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
    PsiClass[] supers = aClass.getSupers();
    for (int i = 0; i < supers.length; i++) {
      PsiClass superClass = supers[i];
      boolean subType = false;
      for (int j = 0; j < supers.length; j++) {
        if (j == i) continue;
        subType |= supers[j].isInheritor(supers[i], true);
      }
      if (subType) continue;
      final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      for (HierarchicalMethodSignature hms : superClass.getVisibleSignatures()) {
        final PsiMethod method = hms.getMethod();
        if (MethodSignatureUtil.findMethodBySignature(aClass, method.getSignature(superClassSubstitutor), false) != null) continue;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) continue;
        final PsiSubstitutor containingClassSubstitutor = TypeConversionUtil.getClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY);
        if (containingClassSubstitutor == null) continue;
        final PsiSubstitutor finalSubstitutor =
          obtainFinalSubstitutor(containingClass, containingClassSubstitutor, hms.getSubstitutor(), false);
        final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, finalSubstitutor, false);
        Set<PsiMethod> methods = overrideEquivalent.get(signature);
        if (methods == null) {
          methods = new LinkedHashSet<PsiMethod>();
          overrideEquivalent.put(signature, methods);
        }
        methods.add(method);
      }
    }
    return overrideEquivalent;
  }
}
