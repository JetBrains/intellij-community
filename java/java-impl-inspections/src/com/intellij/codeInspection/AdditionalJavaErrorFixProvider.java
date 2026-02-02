// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.AbstractJavaErrorFixProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameUnderscoreFix;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassJava10Fix;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.streamMigration.SimplifyForEachInspection;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.JvmMainMethodSearcher;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Some quick-fixes not accessible from the java.analysis module are registered here.
 */
public final class AdditionalJavaErrorFixProvider extends AbstractJavaErrorFixProvider {
  public AdditionalJavaErrorFixProvider() {
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new SimplifyForEachInspection.ForEachNonFinalFix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fixes(JavaErrorKinds.SYNTAX_ERROR, (error, info) -> registerErrorElementFixes(info, error.psi()));
    fix(JavaErrorKinds.UNDERSCORE_IDENTIFIER_UNNAMED, error -> error.psi().getParent() instanceof PsiReferenceExpression ref &&
                                                "_".equals(ref.getReferenceName()) ?
                                                new RenameUnderscoreFix(ref) : null);
    fix(JavaErrorKinds.UNSUPPORTED_FEATURE, error -> {
      if (error.context() != JavaFeature.IMPLICIT_CLASSES) return null;
      PsiMember member = PsiTreeUtil.getNonStrictParentOfType(error.psi(), PsiMember.class);
      if (!(member instanceof PsiMethod)) return null;
      if (!(member.getContainingClass() instanceof PsiImplicitClass implicitClass)) return null;
      boolean hasMainMethod = new JvmMainMethodSearcher() {

        @Override
        public boolean instanceMainMethodsEnabled(@NotNull PsiElement psiElement) {
          return true;
        }

        @Override
        protected boolean inheritedStaticMainEnabled(@NotNull PsiElement psiElement) {
          return true;
        }
      }.hasMainMethod(implicitClass);
      if (!hasMainMethod) return null;
      if (PsiTreeUtil.hasErrorElements(implicitClass)) {
        return null;
      }
      return new ImplicitToExplicitClassBackwardMigrationInspection.ReplaceWithExplicitClassFix(implicitClass);
    });
    fix(JavaErrorKinds.REFERENCE_UNRESOLVED, error -> {
      PsiJavaCodeReferenceElement psi = error.psi();
      if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, psi)) return null;
      if (!(psi instanceof PsiReferenceExpression)) return null;
      if (!(psi.getParent() instanceof PsiReferenceExpression parentReference)) return null;
      if (!(parentReference.getParent() instanceof PsiMethodCallExpression methodCallExpression)) return null;
      if (!MigrateFromJavaLangIoInspection.canBeIOPrint(methodCallExpression)) return null;
      return new MigrateFromJavaLangIoInspection.ConvertIOToSystemOutFix(methodCallExpression)
        .withPresentation(presentation -> presentation.withPriority(PriorityAction.Priority.HIGH));
    });
  }

  private static void registerErrorElementFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                @NotNull PsiErrorElement errorElement) {
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      info.accept(new InsertMissingTokenFix(";"));
      HighlightFixUtil.registerFixesForExpressionStatement(info, parent);
    }
    if (parent instanceof PsiTryStatement tryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      info.accept(new AddExceptionToCatchFix(false));
      info.accept(new AddFinallyFix(tryStatement));
    }
    if (parent instanceof PsiSwitchLabelStatementBase && description.equals(JavaPsiBundle.message("expected.colon.or.arrow"))) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(parent, PsiSwitchBlock.class);
      if (switchBlock != null && switchBlock.getBody() != null) {
        boolean isOld = false;
        boolean isRule = false;
        for (@NotNull PsiElement child : switchBlock.getBody().getChildren()) {
          if (child instanceof PsiSwitchLabeledRuleStatement) {
            isRule = true;
          }
          if (child instanceof PsiSwitchLabelStatement && !PsiTreeUtil.isAncestor(child, parent, false)) {
            isOld = true;
          }
        }
        if (isOld) {
          info.accept(new InsertMissingTokenFix(":", true));
        }
        if (isRule) {
          info.accept(new InsertMissingTokenFix(" ->", true));
        }
      }
    }
  }
}
