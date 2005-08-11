/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.psi.*;

import java.util.List;

public class PsiSuperMethodUtil {
  public static PsiPointcutDef findSuperPointcut(PsiPointcutDef pointcut) {
    return findSuperPointcut(pointcut, pointcut.getContainingAspect());
  }

  private static PsiPointcutDef findSuperPointcut(PsiPointcutDef pointcut, PsiAspect psiAspect) {
    PsiClass superClass = psiAspect.getSuperClass();

    while (!(superClass instanceof PsiAspect) && superClass != null) superClass = superClass.getSuperClass();
    if (superClass == null) return null;

    PsiAspect superAspect = (PsiAspect) superClass;
    return superAspect.findPointcutDefBySignature(pointcut, true);
  }

  public static PsiPointcutDef findDeepestSuperPointcut(PsiPointcutDef pointcut) {
    PsiPointcutDef superPointcut = findSuperPointcut(pointcut);
    PsiPointcutDef prevSuperPointcut = null;

    while (superPointcut != null) {
      prevSuperPointcut = superPointcut;
      superPointcut = findSuperPointcut(prevSuperPointcut);
    }

    return prevSuperPointcut;
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method) {
    return method.findSuperMethods();
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method, boolean checkAccess) {
    return method.findSuperMethods(checkAccess);
  }

  public static PsiMethod[] findSuperMethods(PsiMethod method, PsiClass parentClass) {
    return method.findSuperMethods(parentClass);
  }

  public static List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(PsiMethod method,
                                                                                                boolean checkAccess) {
    return method.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  public static PsiMethod findConstructorInSuper(PsiMethod constructor) {
    if (constructor.getBody() != null) {
      PsiStatement[] statements = constructor.getBody().getStatements();
      if (statements.length > 0) {
        PsiElement firstChild = statements[0].getFirstChild();
        if (firstChild instanceof PsiMethodCallExpression) {
          PsiReferenceExpression superExpr = ((PsiMethodCallExpression)firstChild).getMethodExpression();
          //noinspection HardCodedStringLiteral
          if (superExpr.getText().equals("super")) {
            PsiElement superConstructor = superExpr.resolve();
            if (superConstructor instanceof PsiMethod) {
              return (PsiMethod)superConstructor;
            }
          }
        }
      }
    }

    PsiClass containingClass = constructor.getContainingClass();
    if (containingClass != null) {
      PsiClass superClass = containingClass.getSuperClass();
      if (superClass != null) {
        MethodSignature defConstructor = MethodSignatureUtil.createMethodSignature(superClass.getName(), PsiType.EMPTY_ARRAY,
                                                                                   PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        return MethodSignatureUtil.findMethodBySignature(superClass, defConstructor, false);
      }
    }
    return null;
  }

  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    return method.findDeepestSuperMethod();

  }

  // remove from list all methods overridden by contextClass or its super classes
  public static void removeOverriddenMethods(List<MethodSignatureBackedByPsiMethod> sameSignatureMethods,
                                             PsiClass contextClass,
                                             PsiClass place) {
    for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
      final MethodSignatureBackedByPsiMethod methodBackedMethodSignature1 = sameSignatureMethods.get(i);
      PsiMethod method1 = methodBackedMethodSignature1.getMethod();
      final PsiClass class1 = method1.getContainingClass();
      if (method1.hasModifierProperty(PsiModifier.STATIC) || method1.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      // check if method1 is overridden
      boolean overridden = false;
      for (int j = 0; j < sameSignatureMethods.size(); j++) {
        if (i==j) continue;
        final MethodSignatureBackedByPsiMethod methodBackedMethodSignature2 = sameSignatureMethods.get(j);
        if (MethodSignatureUtil.isSubsignature(methodBackedMethodSignature1, methodBackedMethodSignature2)) {
          PsiMethod method2 = methodBackedMethodSignature2.getMethod();
          final PsiClass class2 = method2.getContainingClass();
          if (InheritanceUtil.isInheritorOrSelf(class2, class1, true)
              // method from interface cannot override method from Object
              && !(!place.isInterface() && "java.lang.Object".equals(class1.getQualifiedName()) && class2.isInterface())
              && !(method1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !method1.getManager().arePackagesTheSame(class1, class2))) {
            overridden = true;
            break;
          }
          // check for sibling override: class Context extends Implementations implements Declarations {}
          // see JLS 8.4.6.4
          if (!method2.hasModifierProperty(PsiModifier.ABSTRACT)
              && PsiUtil.isAccessible(method1, contextClass, contextClass)
              && PsiUtil.isAccessible(method2, contextClass, contextClass)) {
            overridden = true;
            break;
          }
        }
      }
      if (overridden) {
        sameSignatureMethods.remove(i);
      }
    }
  }
}
