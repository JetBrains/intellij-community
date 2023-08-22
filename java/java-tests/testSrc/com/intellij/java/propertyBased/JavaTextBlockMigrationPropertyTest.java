// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.SuppressionUtil;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
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
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    return JAVA_15;
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
      MadTestingUtil.performOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"),
                                           this::transformContent);
    PropertyChecker.
      checkScenarios(fileAction);
  }

  private void transformContent(@NotNull ImperativeCommand.Environment env, @NotNull VirtualFile file) {
    List<PsiPolyadicExpression> concatenations =
      ContainerUtil.filter(PsiTreeUtil.findChildrenOfType(getPsiManager().findFile(file), PsiPolyadicExpression.class),
                           e -> e.getType() != null && e.getType().equalsToText(JAVA_LANG_STRING));
    if (concatenations.isEmpty()) return;

    myFixture.openFileInEditor(file);
    MigrationInvoker migrationInvoker = new MigrationInvoker();
    BackwardMigrationInvoker backwardMigrationInvoker = new BackwardMigrationInvoker();
    for (PsiPolyadicExpression concatenation : concatenations) {
      if (SuppressionUtil.isSuppressed(concatenation, MigrationInvoker.getToolId()) ||
          SuppressionUtil.isSuppressed(concatenation, BackwardMigrationInvoker.getToolId())) {
        continue;
      }
      PsiExpression[] operands = concatenation.getOperands();
      if (operands.length < 2) continue;

      env.logMessage("Tweaking concatenation at " + concatenation.getTextRange());

      List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(operands[0]);
      // IDEA-224671
      if (injected != null && !injected.isEmpty()) continue;
      String expected = getConcatenationText(operands);
      if (expected == null || countNewLines(expected) < 2 && getQuoteIndex(expected) == -1) continue;
      expected = replaceUnescapedSpaces(expected);

      Computable<PsiElement> replaceAction = () -> {
        PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        PsiExpression newExpression = factory.createExpressionFromText("(" + concatenation.getText() + ")", null);
        return concatenation.replace(newExpression);
      };
      PsiExpression parent = (PsiExpression)WriteCommandAction.runWriteCommandAction(concatenation.getProject(), replaceAction);
      PsiPolyadicExpression replaced = (PsiPolyadicExpression)PsiUtil.skipParenthesizedExprDown(parent);

      invokeIntention(replaced, myFixture, migrationInvoker);
      PsiLiteralExpression textBlock = (PsiLiteralExpression)PsiUtil.skipParenthesizedExprDown(parent);

      invokeIntention(textBlock, myFixture, backwardMigrationInvoker);
      PsiElement element = PsiUtil.skipParenthesizedExprDown(parent);
      PsiExpression[] operandsAfter = getOperands(element);

      String actual = Objects.requireNonNull(getConcatenationText(operandsAfter));
      assertEquals("concatenation content", expected, actual);
    }
  }
  
  private static PsiExpression[] getOperands(@NotNull PsiElement element) {
    PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(element, PsiPolyadicExpression.class);
    if (polyadicExpression != null) return polyadicExpression.getOperands();
    PsiLiteralExpression operand = (PsiLiteralExpression)element;
    assertFalse(operand.isTextBlock());
    return new PsiExpression[]{operand};
  }

  @NotNull
  private static String replaceUnescapedSpaces(@NotNull String text) {
    if (!text.contains("040")) return text;
    StringBuilder result = new StringBuilder();
    int i = 0;
    int length = text.length();
    while (i < length) {
      int nSlashes = 0;
      int next;
      while (i < length && (next = PsiLiteralUtil.parseBackSlash(text, i)) != -1) {
        nSlashes++;
        i = next;
      }
      if (i >= length) {
        result.append(StringUtil.repeatSymbol('\\', nSlashes));
        break;
      }
      if (nSlashes % 2 != 0 && StringUtil.startsWith(text, i, "040")) {
        result.append(StringUtil.repeatSymbol('\\', nSlashes - 1)).append(" ");
        i += 3;
        continue;
      }
      result.append(StringUtil.repeatSymbol('\\', nSlashes)).append(text.charAt(i));
      i++;
    }
    return result.toString();
  }

  @Nullable
  private static String getConcatenationText(PsiExpression @NotNull [] operands) {
    String[] lines = new String[operands.length];
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiLiteralExpression literal = ObjectUtils.tryCast(operand, PsiLiteralExpression.class);
      if (literal == null) return null;
      String line;
      if (ExpressionUtils.hasStringType(literal) && !literal.isTextBlock()) {
        line = PsiLiteralUtil.getStringLiteralContent(literal);
      }
      else {
        Object value = literal.getValue();
        line = value == null ? null : value.toString();
      }
      if (line == null) return null;
      lines[i] = line;
    }
    // IDEA-226395
    String[] textBlockLines = getTextBlockLines(lines);
    int indent = PsiLiteralUtil.getTextBlockIndent(textBlockLines, true, true);
    if (indent > 0 && textBlockLines.length > 0 && textBlockLines[textBlockLines.length - 1].endsWith("\n")) indent = 0;
    if (indent > 0) return null;

    return StringUtil.join(lines);
  }

  @NotNull
  private static String @NotNull [] getTextBlockLines(String @NotNull [] lines) {
    String blockLines = PsiLiteralUtil.escapeTextBlockCharacters(StringUtil.join(lines), true, true, true);
    return blockLines.split("(?<=\n)");
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

  private static int getQuoteIndex(@NotNull String text) {
    return getEscapedCharIndex(text, 0, '"');
  }

  private static int getNewLineIndex(@NotNull String text, int start) {
    return getEscapedCharIndex(text, start, 'n');
  }

  private static int getEscapedCharIndex(@NotNull String text, int start, char escapedChar) {
    int slashes = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        slashes++;
      }
      else if (c == escapedChar && slashes % 2 != 0) {
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
      Integer firstNewLineIndex = null;
      for (PsiExpression operand : e.getOperands()) {
        PsiExpression literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiLiteralExpression.class);
        if (literal == null) continue;
        String text = literal.getText();
        int idx = getNewLineIndex(text, 0);
        if (idx == -1) continue;
        if (firstNewLineIndex != null) return firstNewLineIndex;
        firstNewLineIndex = literal.getTextOffset() + idx;
        if (getNewLineIndex(text, idx) != -1) return firstNewLineIndex;
      }
      return e.getTextOffset();
    }

    @NotNull
    @Override
    public String getFixHint() {
      return "Replace with text block";
    }

    @NotNull
    private static String getToolId() {
      return "TextBlockMigration";
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

    @NotNull
    private static String getToolId() {
      return "TextBlockBackwardMigration";
    }
  }
}
