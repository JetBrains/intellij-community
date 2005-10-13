/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.HierarchicalMethodSignature;

import java.util.List;
import java.util.Set;
import java.util.Collection;

public class ClassUtil {
  public static PsiMethod getAnyAbstractMethod(PsiClass aClass, Collection<HierarchicalMethodSignature> allMethodsCollection) {
    PsiMethod methodToImplement = getAnyMethodToImplement(aClass, allMethodsCollection);
    if (methodToImplement != null) {
      return methodToImplement;
    }
    PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    return null;

/*
    // the only remaining possiblity for class to have abstract method here is
    //  from package local abstract method defined in inherited class from other package
    PsiManager manager = aClass.getManager();
    for (List<MethodSignatureBackedByPsiMethod> sameSignatureMethods : allMethodsCollection.values()) {
      // look for abstract package locals
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        MethodSignatureBackedByPsiMethod methodSignature1 = sameSignatureMethods.get(i);
        PsiMethod method1 = methodSignature1.getMethod();
        PsiClass class1 = method1.getContainingClass();
        if (class1 == null) {
          sameSignatureMethods.remove(i);
          continue;
        }
        if (!method1.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        // check if abstract package local method gets overriden by not abstract
        // i.e. there is direct subclass in the same package which overrides this method
        for (int j = sameSignatureMethods.size() - 1; j >= 0; j--) {
          MethodSignatureBackedByPsiMethod methodSignature2 = sameSignatureMethods.get(j);
          PsiMethod method2 = methodSignature2.getMethod();
          PsiClass class2 = method2.getContainingClass();
          if (i == j || class2 == null) continue;
          if (class2.isInheritor(class1, true)
              // NB! overriding method may be abstract
//              && !method2.hasModifierProperty(PsiModifier.ABSTRACT)
&& manager.arePackagesTheSame(class1, class2)) {
            sameSignatureMethods.remove(i);
            break;
          }
        }
      }
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        MethodSignatureBackedByPsiMethod methodSignature = sameSignatureMethods.get(i);
        PsiMethod method = methodSignature.getMethod();
        PsiClass class1 = method.getContainingClass();
        if (class1 == null
            || !method.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        return method;
      }
    }
    return null;
*/
  }

  public static PsiMethod getAnyMethodToImplement(PsiClass aClass, Collection<HierarchicalMethodSignature> allMethodsCollection) {
    for (HierarchicalMethodSignature signatureHierarchical : allMethodsCollection) {
      final PsiMethod method = signatureHierarchical.getMethod();
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        continue;
      }
      final PsiResolveHelper resolveHelper = aClass.getManager().getResolveHelper();
      if (!aClass.equals(containingClass) &&
          method.hasModifierProperty(PsiModifier.ABSTRACT)
          && !method.hasModifierProperty(PsiModifier.STATIC)
          && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return method;
      } else {
        final List<HierarchicalMethodSignature> superSignatures = signatureHierarchical.getSuperSignatures();
        for (HierarchicalMethodSignature superSignatureHierarchical : superSignatures) {
          final PsiMethod superMethod = superSignatureHierarchical.getMethod();
          if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
              !resolveHelper.isAccessible(superMethod, method, null)) return superMethod;
        }
      }
    }

    return null;
  }

  public static TextRange getClassDeclarationTextRange(PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return aClass.getLBrace().getTextRange();
    }
    final PsiElement psiElement = aClass instanceof PsiAnonymousClass
                                  ? ((PsiAnonymousClass)aClass).getBaseClassReference()
                                  : aClass.getModifierList() == null ? (PsiElement)aClass.getNameIdentifier() : aClass.getModifierList();
    if(psiElement == null) return new TextRange(aClass.getTextRange().getStartOffset(), aClass.getTextRange().getStartOffset());
    TextRange startTextRange = psiElement.getTextRange();
    int start = startTextRange == null ? 0 : startTextRange.getStartOffset();
    TextRange endTextRange = (aClass instanceof PsiAnonymousClass
                              ? (PsiElement)((PsiAnonymousClass)aClass).getBaseClassReference()
                              : aClass.getImplementsList()).getTextRange();
    int end = endTextRange == null ? start : endTextRange.getEndOffset();
    return new TextRange(start, end);
  }
}