// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.TextBlockBackwardMigrationInspection;
import com.intellij.codeInspection.TextBlockMigrationInspection;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
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
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
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
                                           file -> Generator.constant(env -> transformContent(file)));
    PropertyChecker.checkScenarios(fileAction);
  }

  private void transformContent(@NotNull PsiFile file) {
    List<PsiPolyadicExpression> concatenations =
      ContainerUtil.filter(PsiTreeUtil.findChildrenOfType(file, PsiPolyadicExpression.class),
                           e -> e.getType() != null && e.getType().equalsToText(JAVA_LANG_STRING));
    if (concatenations.isEmpty()) return;

    myFixture.openFileInEditor(file.getVirtualFile());
    for (PsiPolyadicExpression concatenation : concatenations) {
      PsiExpression[] operands = concatenation.getOperands();
      if (operands.length < 2) continue;
      List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(file.getProject()).getInjectedPsiFiles(operands[0]);
      // IDEA-224671
      if (injected != null && !injected.isEmpty()) continue;
      String expected = getConcatenationText(operands);
      if (expected == null || countNewLines(expected) < 2) continue;
      expected = expected.replaceAll("\\\\040", " ");

      Computable<PsiElement> replaceAction = () -> {
        PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
        PsiExpression newExpression = factory.createExpressionFromText("(" + concatenation.getText() + ")", null);
        return concatenation.replace(newExpression);
      };
      PsiExpression parent = (PsiExpression)WriteCommandAction.runWriteCommandAction(concatenation.getProject(), replaceAction);
      PsiPolyadicExpression replaced = (PsiPolyadicExpression)PsiUtil.skipParenthesizedExprDown(parent);

      invokeIntention(replaced, myFixture, new MigrationInvoker());
      PsiLiteralExpression textBlock = (PsiLiteralExpression)PsiUtil.skipParenthesizedExprDown(parent);

      invokeIntention(textBlock, myFixture, new BackwardMigrationInvoker());
      PsiElement element = PsiUtil.skipParenthesizedExprDown(parent);
      PsiPolyadicExpression after = (PsiPolyadicExpression)element;

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

  private static <T extends PsiElement> void invokeIntention(@NotNull T element,
                                                             @NotNull CodeInsightTestFixture fixture,
                                                             @NotNull ActionInvoker<T> action) {
    Editor editor = fixture.getEditor();
    if (editor == null) return;
    editor.getCaretModel().moveToOffset(action.generateOffset(element));
    List<IntentionAction> actions = fixture.filterAvailableIntentions(action.getFixHint());
    if (actions.size() == 1) fixture.launchAction(actions.get(0));
  }

  private static int countNewLines(@NotNull String text) {
    int cnt = 0;
    int i = getNewLineIndex(text, 0);
    while (i != -1) {
      cnt++;
      i = getNewLineIndex(text, i);
    }
    return cnt;
  }

  private static int getNewLineIndex(@NotNull String text, int start) {
    int slashes = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        slashes++;
      }
      else if (c == 'n' && slashes % 2 != 0) {
        return i;
      }
      else {
        slashes = 0;
      }
    }
    return -1;
  }

  private interface ActionInvoker<T extends PsiElement> {

    int generateOffset(@NotNull T e);

    @NotNull
    String getFixHint();
  }

  private static class MigrationInvoker implements ActionInvoker<PsiPolyadicExpression> {
    @Override
    public int generateOffset(@NotNull PsiPolyadicExpression e) {
      for (PsiExpression operand : e.getOperands()) {
        PsiExpression literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiLiteralExpression.class);
        if (literal == null) continue;
        int idx = getNewLineIndex(literal.getText(), 0);
        if (idx != -1) return literal.getTextOffset() + idx;
      }
      return -1;
    }

    @NotNull
    @Override
    public String getFixHint() {
      return "Replace with text block";
    }
  }

  private static class BackwardMigrationInvoker implements ActionInvoker<PsiLiteralExpression> {
    @Override
    public int generateOffset(@NotNull PsiLiteralExpression e) {
      return e.getTextOffset();
    }

    @NotNull
    @Override
    public String getFixHint() {
      return "Replace with regular string literal";
    }
  }
}
