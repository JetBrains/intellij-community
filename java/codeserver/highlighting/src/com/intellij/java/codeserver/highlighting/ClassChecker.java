// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ClassChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ClassChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  /**
   * new ref(...) or new ref(...) { ... } where ref is abstract class
   */
  void checkAbstractInstantiation(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiAnonymousClass aClass
        && parent.getParent() instanceof PsiNewExpression
        && !PsiUtilCore.hasErrorElementChild(parent.getParent())) {
      checkClassWithAbstractMethods(aClass, aClass, ref.getTextRange());
    }
  }

  private void checkClassWithAbstractMethods(@NotNull PsiClass aClass, @NotNull PsiMember implementsFixElement, @NotNull TextRange range) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
    if (abstractMethod == null) {
      return;
    }

    PsiClass containingClass = abstractMethod.getContainingClass();
    if (containingClass == null ||
        containingClass == aClass ||
        implementsFixElement instanceof PsiEnumConstant && !hasEnumConstantsWithInitializer(aClass)) {
      return;
    }

    myVisitor.report(JavaErrorKinds.CLASS_NO_ABSTRACT_METHOD.create(implementsFixElement, abstractMethod));
  }

  void checkExtendsDuplicate(@NotNull PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(element.getParent() instanceof PsiReferenceList list)) return;
    if (!(list.getParent() instanceof PsiClass)) return;
    if (!(resolved instanceof PsiClass aClass)) return;
    PsiManager manager = myVisitor.file().getManager();
    PsiJavaCodeReferenceElement sibling = PsiTreeUtil.getPrevSiblingOfType(element, PsiJavaCodeReferenceElement.class);
    while (true) {
      if (sibling == null) return;
      PsiElement target = sibling.resolve();
      if (manager.areElementsEquivalent(target, aClass)) break;
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiJavaCodeReferenceElement.class);
    }
    myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_DUPLICATE.create(element, aClass));
  }

  void checkClassExtendsForeignInnerClass(@NotNull PsiJavaCodeReferenceElement extendRef, @Nullable PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) return;
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass aClass)) return;
    PsiClass containerClass;
    if (aClass instanceof PsiTypeParameter typeParameter) {
      if (!(typeParameter.getOwner() instanceof PsiClass cls)) return;
      containerClass = cls;
    }
    else {
      containerClass = aClass;
    }
    if (aClass.getExtendsList() != parent && aClass.getImplementsList() != parent) return;
    if (resolved != null && !(resolved instanceof PsiClass)) {
      myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_NAME_EXPECTED.create(extendRef));
      return;
    }
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass base) {
          PsiClass baseClass = base.getContainingClass();
          if (baseClass != null &&
              base.hasModifierProperty(PsiModifier.PRIVATE) &&
              baseClass == containerClass &&
              baseClass.getContainingClass() == null) {
            myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_INNER_PRIVATE.create(reference, base));
            stopWalking();
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved &&
              baseClass != null &&
              (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.hasModifierProperty(PsiModifier.STATIC)) &&
              !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, psiClass -> psiClass != aClass, true) &&
              !qualifiedNewCalledInConstructors(aClass)) {
            myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_NO_ENCLOSING_INSTANCE.create(extendRef, baseClass));
            stopWalking();
          }
        }
      }
    });
  }

  /**
   * 15.9 Class Instance Creation Expressions | 15.9.2 Determining Enclosing Instances
   */
  private static boolean qualifiedNewCalledInConstructors(@NotNull PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) return false;
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression methodCallExpression = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (methodCallExpression == null) return false;
      if (JavaPsiConstructorUtil.isChainedConstructorCall(methodCallExpression)) continue;
      PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      //If the class instance creation expression is qualified, then the immediately
      //enclosing instance of i is the object that is the value of the Primary expression or the ExpressionName,
      //otherwise aClass needs to be a member of a class enclosing the class in which the class instance creation expression appears
      //already excluded by InheritanceUtil.hasEnclosingInstanceInScope
      if (qualifierExpression == null) return false;
    }
    return true;
  }

  private static boolean hasEnumConstantsWithInitializer(@NotNull PsiClass aClass) {
    return CachedValuesManager.getCachedValue(aClass, () -> {
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (field instanceof PsiEnumConstant constant && constant.getInitializingClass() != null) {
          return new CachedValueProvider.Result<>(true, PsiModificationTracker.MODIFICATION_COUNT);
        }
      }
      return new CachedValueProvider.Result<>(false, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
