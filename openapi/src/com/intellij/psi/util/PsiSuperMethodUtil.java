/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    return constructor.findConstructorInSuper();
  }

  public static PsiMethod findDeepestSuperMethod(PsiMethod method) {
    return method.findDeepestSuperMethod();

  }

  /**
   * @deprecated
   * @return all overridden methods sorted by hierarchy,
   * i.e  Map: PsiMethod method -> List of overridden methods (access control rules are respected)
   */
  public static Map<PsiMethod, List<PsiMethod>> getMethodHierarchy(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    return getMethodHierarchy(method, aClass);
  }

  /**
   * @deprecated
   * @param method
   * @param aClass
   * @return
   */
  public static Map<PsiMethod, List<PsiMethod>> getMethodHierarchy(PsiMethod method, PsiClass aClass) {
    Map<PsiMethod, List<PsiMethod>> map = new HashMap<PsiMethod, List<PsiMethod>>();
    List<PsiMethod> allMethods = new ArrayList<PsiMethod>();
    getMethodHierarchy(method, aClass, map, allMethods);
    return map;
  }

  /**
   * @deprecated
   * @param method
   * @param aClass
   * @param map
   * @param allMethods
   */
  private static void getMethodHierarchy(PsiMethod method, PsiClass aClass, Map<PsiMethod, List<PsiMethod>> map, List<PsiMethod> allMethods) {
    final PsiClass[] superTypes = aClass.getSupers();
    final int startMethodIndex = allMethods.size();
    for (int i = 0; i < superTypes.length; i++) {
      PsiClass superType = superTypes[i];
      final PsiMethod superMethod;
      superMethod = MethodSignatureUtil.findMethodBySignature(superType, method, false);
      if (superMethod == null) {
        getMethodHierarchy(method, superType, map, allMethods);
      }
      else {
        if (PsiUtil.isAccessible(superMethod, aClass, aClass)) {
          allMethods.add(superMethod);
        }
      }
    }
    final int endMethodIndex = allMethods.size();
    map.put(method, new ArrayList<PsiMethod>(allMethods.subList(startMethodIndex, endMethodIndex)));
    for (int i = startMethodIndex; i < endMethodIndex; i++) {
      final PsiMethod superMethod = allMethods.get(i);
      if (map.get(superMethod) == null) {
        getMethodHierarchy(superMethod, superMethod.getContainingClass(), map, allMethods);
      }
    }
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
      if (overridden) {
        sameSignatureMethods.remove(i);
      }
    }
  }
}
