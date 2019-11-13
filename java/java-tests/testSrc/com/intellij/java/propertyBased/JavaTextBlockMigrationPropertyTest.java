// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInspection.TextBlockBackwardMigrationInspection;
import com.intellij.codeInspection.TextBlockMigrationInspection;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import com.intellij.testFramework.propertyBased.InvokeIntention;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

@SkipSlowTestLocally
public class JavaTextBlockMigrationPropertyTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TextBlockMigrationInspection(), new TextBlockBackwardMigrationInspection());
  }

  @Override
  protected void tearDown() throws Exception {
    // remove jdk if it was created during highlighting to avoid leaks
    try {
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPreserveStringContent() {
    Supplier<MadTestingAction> fileAction =
      MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"),
                                           file -> Generator.constant(env -> transformContent(env, file)));
    PropertyChecker.checkScenarios(fileAction);
  }

  private static void transformContent(@NotNull ImperativeCommand.Environment env, @NotNull PsiFile file) {
    List<PsiPolyadicExpression> concatenations =
      ContainerUtil.filter(PsiTreeUtil.findChildrenOfType(file, PsiPolyadicExpression.class),
                           e -> e.getType() != null && e.getType().equalsToText(JAVA_LANG_STRING));

    for (PsiPolyadicExpression concatenation : concatenations) {
      PsiExpression[] operands = concatenation.getOperands();
      if (operands.length < 2) continue;
      List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(file.getProject()).getInjectedPsiFiles(operands[0]);
      // IDEA-224671
      if (injected != null && !injected.isEmpty()) continue;
      String expected = getConcatenationText(operands);
      if (expected == null || StringUtil.getOccurrenceCount(expected, "\\n") < 2) continue;
      expected = expected.replaceAll("\\\\040", " ");

      Computable<PsiElement> replaceAction = () -> {
        PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
        PsiExpression newExpression = factory.createExpressionFromText("(" + concatenation.getText() + ")", null);
        return concatenation.replace(newExpression);
      };
      PsiExpression parent = (PsiExpression)WriteCommandAction.runWriteCommandAction(concatenation.getProject(), replaceAction);
      PsiPolyadicExpression replaced = (PsiPolyadicExpression)PsiUtil.skipParenthesizedExprDown(parent);

      new InvokeIntentionAroundConcatenation(replaced).performCommand(env);

      PsiLiteralExpression textBlock = (PsiLiteralExpression)PsiUtil.skipParenthesizedExprDown(parent);
      new InvokeIntentionAroundTextBlock(textBlock).performCommand(env);

      PsiElement element = PsiUtil.skipParenthesizedExprDown(parent);
      PsiPolyadicExpression after = (PsiPolyadicExpression) element;

      String actual = Objects.requireNonNull(getConcatenationText(after.getOperands()));
      assertEquals("concatenation content", expected, actual);
    }
  }

  @Nullable
  private static String getConcatenationText(@NotNull PsiExpression[] operands) {
    String[] lines = new String[operands.length];
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiLiteralExpressionImpl literal = ObjectUtils.tryCast(operand, PsiLiteralExpressionImpl.class);
      if (literal == null) return null;
      String line;
      if (literal.getLiteralElementType() == JavaTokenType.STRING_LITERAL) {
        line = literal.getInnerText();
      }
      else {
        Object value = literal.getValue();
        line = value == null ? null : value.toString();
      }
      if (line == null) return null;
      lines[i] = line;
    }
    // IDEA-226395
    if (PsiLiteralUtil.getTextBlockIndent(lines) != 0) return null;
    return StringUtil.join(lines);
  }

  private static class InvokeIntentionAroundTextBlock extends InvokeIntention {

    private final PsiLiteralExpression myTextBlock;

    InvokeIntentionAroundTextBlock(@NotNull PsiLiteralExpression textBlock) {
      super(textBlock.getContainingFile(), new IntentionPolicy() {
        @Override
        protected boolean shouldSkipIntention(@NotNull String actionText) {
          return !"Replace with regular string literal".equals(actionText);
        }
      });
      myTextBlock = textBlock;
    }

    @Override
    protected int generateDocOffset(@NotNull Environment env, @Nullable String logMessage) {
      return myTextBlock.getTextOffset();
    }
  }

  private static class InvokeIntentionAroundConcatenation extends InvokeIntention {

    private final PsiPolyadicExpression myConcatenation;

    InvokeIntentionAroundConcatenation(@NotNull PsiPolyadicExpression concatenation) {
      super(concatenation.getContainingFile(), new IntentionPolicy() {
        @Override
        protected boolean shouldSkipIntention(@NotNull String actionText) {
          return !"Replace with text block".equals(actionText);
        }
      });
      myConcatenation = concatenation;
    }

    @Override
    protected int generateDocOffset(@NotNull ImperativeCommand.Environment env, @Nullable String logMessage) {
      for (PsiExpression operand : myConcatenation.getOperands()) {
        PsiExpression literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiLiteralExpression.class);
        if (literal == null) continue;
        int idx = literal.getText().indexOf("\\n");
        if (idx != -1) return literal.getTextOffset() + idx;
      }
      return -1;
    }
  }
}
