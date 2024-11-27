// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AddAssertNonNullFromTestFrameworksFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
  private final String myText;
  private final SmartPsiElementPointer<PsiExpression> myQualifierPointer;
  private final Variant myVariant;

  public AddAssertNonNullFromTestFrameworksFix(@NotNull PsiExpression qualifier, Variant variant) {
    myText = qualifier.getText();
    myQualifierPointer = SmartPointerManager.getInstance(qualifier.getProject()).createSmartPsiElementPointer(qualifier);
    myVariant = variant;
  }

  public enum Variant {
    JUNIT_3("JUnit 3", "assertNotNull"),
    JUNIT_4("JUnit 4", "Assert.assertNotNull"),
    JUNIT_5("JUnit 5", "Assertions.assertNotNull"),
    TESTNG("TestNG", "Assert.assertNotNull");

    /// Used only for presentation purposes.
    public final String name;

    /// Used only for presentation purposes.
    public final String replacement;

    Variant(String name, String replacement) {
      this.name = name;
      this.replacement = replacement;
    }
  }

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("inspection.testframework.assert.quickfix", myVariant.name, myVariant.replacement + "(" + myText + ")");
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.quickfix.assert.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression qualifier = updater.getWritable(myQualifierPointer.getElement());

    PsiExpression expr = PsiTreeUtil.getNonStrictParentOfType(qualifier, PsiExpression.class);
    if (expr == null) return;
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expr);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    expr = result.getExpression();
    PsiElement anchorElement = result.getAnchor();

    // If the element before our qualifier is an inspection suppression comment, then we want to
    // add assertion before this suppression comment so it's not accidentally disabled.
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorElement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    String text = switch (myVariant) {
      case JUNIT_3 -> "assertNotNull(" + myText + ")";
      case JUNIT_4 -> "org.junit.Assert.assertNotNull(" + myText + ")";
      case JUNIT_5 -> "org.junit.jupiter.api.Assertions.assertNotNull(" + myText + ")";
      case TESTNG -> "org.testng.Assert.assertNotNull(" + myText + ")";
    } + ";";
    PsiStatement assertStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(text, expr);
    PsiElement added = anchorElement.getParent().addBefore(assertStatement, anchorElement);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);
  }

  public static @Nullable Variant isAvailable(@NotNull PsiExpression expression) {
    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (containingMethod == null) return null;
    PsiClass containingClass = containingMethod.getContainingClass();
    if (containingClass == null) return null;
    TestFramework detectedTestFramework = TestFrameworks.detectFramework(containingClass);
    if (detectedTestFramework == null) return null;
    return switch (detectedTestFramework.getName()) {
      case "JUnit3" -> Variant.JUNIT_3;
      case "JUnit4" -> Variant.JUNIT_4;
      case "JUnit5" -> Variant.JUNIT_5;
      case "TestNG" -> Variant.TESTNG;
      default -> null;
    };
  }
}
