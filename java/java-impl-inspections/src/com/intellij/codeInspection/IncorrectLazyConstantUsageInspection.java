// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;

public final class IncorrectLazyConstantUsageInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher LAZY_COLLECTION_FACTORIES = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_UTIL_LIST, "ofLazy"),
    CallMatcher.staticCall(JAVA_UTIL_MAP, "ofLazy")
  );

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAZY_CONSTANTS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(@NotNull PsiField field) {
        if (field.hasModifierProperty(PsiModifier.FINAL)) return;
        PsiType type = field.getType();
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass != null && "java.lang.LazyConstant".equals(aClass.getQualifiedName())) {
          holder.registerProblem(field.getNameIdentifier(), JavaBundle.message("inspection.incorrect.lazy.constant.usage.message"),
                                 MakeFieldFinalFix.buildFixUnconditional(field));
          return;
        }
        if (isLazyCollectionInitializer(field)) {
          holder.registerProblem(field.getNameIdentifier(),
                                 JavaBundle.message("inspection.incorrect.lazy.constant.usage.lazy.collection.message"),
                                 MakeFieldFinalFix.buildFixUnconditional(field));
        }
      }
    };
  }

  public static boolean isLazyCollectionInitializer(@NotNull PsiField field) {
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(field.getInitializer());
    return initializer instanceof PsiMethodCallExpression call && LAZY_COLLECTION_FACTORIES.test(call);
  }
}
