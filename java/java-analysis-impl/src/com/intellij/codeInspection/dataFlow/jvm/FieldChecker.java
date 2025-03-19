// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldChecker {
  private final boolean myTrustDirectFieldInitializers;
  private final boolean myTrustFieldInitializersInConstructors;
  private final boolean myCanInstantiateItself;
  private final PsiClass myClass;
  private final FieldChecker myParent;

  private FieldChecker(PsiElement context) {
    PsiMethod method = ObjectUtils.tryCast(PsiTreeUtil.getNonStrictParentOfType(context, PsiMember.class), PsiMethod.class);
    PsiClass contextClass = method != null ? method.getContainingClass() : context instanceof PsiClass ? (PsiClass)context : null;
    myClass = contextClass;
    if (method == null || myClass == null) {
      myTrustDirectFieldInitializers = myTrustFieldInitializersInConstructors = myCanInstantiateItself = false;
      myParent = null;
      return;
    }
    myParent = PsiUtil.isLocalOrAnonymousClass(myClass) ? new FieldChecker(myClass.getParent()) : null;
    // Indirect instantiation via other class is still possible, but hopefully unlikely
    ClassInitializationInfo info = CachedValuesManager.getProjectPsiDependentCache(contextClass, ClassInitializationInfo::new);
    myCanInstantiateItself = info.myCanInstantiateItself;
    if (method.hasModifierProperty(PsiModifier.STATIC) || method.isConstructor()) {
      myTrustDirectFieldInitializers = true;
      myTrustFieldInitializersInConstructors = false;
      return;
    }
    myTrustFieldInitializersInConstructors = !info.mySuperCtorsCallMethods && !info.myCtorsCallMethods;
    myTrustDirectFieldInitializers = !info.mySuperCtorsCallMethods;
  }

  public boolean canTrustFieldInitializer(PsiField field) {
    if (myParent != null && !myParent.canTrustFieldInitializer(field)) return false;
    if (field.hasInitializer()) {
      boolean staticField = field.hasModifierProperty(PsiModifier.STATIC);
      if (staticField && myClass != null && field.getContainingClass() != myClass) return true;
      return myTrustDirectFieldInitializers && (!myCanInstantiateItself || !staticField);
    }
    return myTrustFieldInitializersInConstructors;
  }

  public static FieldChecker getChecker(@Nullable PsiElement context) {
    if (context == null) return new FieldChecker(null);
    return CachedValuesManager.getProjectPsiDependentCache(context, FieldChecker::new);
  }

  private static class ClassInitializationInfo {
    private static final CallMatcher SAFE_CALLS =
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "requireNonNull");

    final boolean myCanInstantiateItself;
    final boolean myCtorsCallMethods;
    final boolean mySuperCtorsCallMethods;

    ClassInitializationInfo(@NotNull PsiClass psiClass) {
      // Indirect instantiation via other class is still possible, but hopefully unlikely
      boolean canInstantiateItself = false;
      for (PsiElement child = psiClass.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiMember && ((PsiMember)child).hasModifierProperty(PsiModifier.STATIC) &&
            SyntaxTraverser.psiTraverser(child).filter(PsiNewExpression.class)
              .filterMap(PsiNewExpression::getClassReference)
              .find(classRef -> classRef.isReferenceTo(psiClass)) != null) {
          canInstantiateItself = true;
          break;
        }
      }
      myCanInstantiateItself = canInstantiateItself;
      mySuperCtorsCallMethods =
        !InheritanceUtil.processSupers(psiClass, false, superClass -> !canCallMethodsInConstructors(superClass, true));
      myCtorsCallMethods = canCallMethodsInConstructors(psiClass, false);
    }

    private static boolean canCallMethodsInConstructors(@NotNull PsiClass aClass, boolean virtual) {
      boolean inByteCode = false;
      if (aClass instanceof PsiCompiledElement) {
        inByteCode = true;
        PsiElement navigationElement = aClass.getNavigationElement();
        if (navigationElement instanceof PsiClass) {
          aClass = (PsiClass)navigationElement;
        }
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (inByteCode && JavaMethodContractUtil.isPure(constructor) &&
            !JavaMethodContractUtil.hasExplicitContractAnnotation(constructor)) {
          // While pure constructor may call pure overridable method, our current implementation
          // of bytecode inference will not infer the constructor purity in this case.
          // So if we inferred a constructor purity from bytecode we can currently rely that
          // no overridable methods are called there.
          continue;
        }
        if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return true;

        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;

        for (PsiMethodCallExpression call : SyntaxTraverser.psiTraverser().withRoot(body).filter(PsiMethodCallExpression.class)) {
          PsiReferenceExpression methodExpression = call.getMethodExpression();
          if (methodExpression.textMatches(PsiKeyword.THIS) || methodExpression.textMatches(PsiKeyword.SUPER)) continue;
          if (SAFE_CALLS.test(call)) continue;
          if (!virtual) return true;

          PsiMethod target = call.resolveMethod();
          if (target != null && PsiUtil.canBeOverridden(target)) return true;
        }
      }

      return false;
    }
  }
}
