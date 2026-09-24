// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import org.jetbrains.annotations.NonNls;
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
    JUNIT_3("JUnit 3", "assertNotNull", "assertNotNull"),
    JUNIT_4("JUnit 4", "Assert.assertNotNull", "org.junit.Assert.assertNotNull"),
    JUNIT_5("JUnit 5", "Assertions.assertNotNull", "org.junit.jupiter.api.Assertions.assertNotNull"),
    TESTNG("TestNG", "Assert.assertNotNull", "org.testng.Assert.assertNotNull");

    /// Used only for presentation purposes.
    public final String name;

    /// Used only for presentation purposes.
    public final String replacement;

    /// Reference to the assertion method to generate a call to; must be shortened after the generation.
    public final @NonNls String methodReference;

    Variant(String name, String replacement, @NonNls String methodReference) {
      this.name = name;
      this.replacement = replacement;
      this.methodReference = methodReference;
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
    PsiElement anchorElement = result.getSuppressionAwareAnchor();

    @NonNls String text = myVariant.methodReference + "(" + myText + ");";
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
