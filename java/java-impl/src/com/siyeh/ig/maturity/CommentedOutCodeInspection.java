// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class CommentedOutCodeInspection extends BaseInspection {

  public int minLines = 2;

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.commented.out.code.problem.descriptor", infos[0]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("minLines", InspectionGadgetsBundle.message("inspection.commented.out.code.min.lines.options"), 1,
             1000));
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    int lines = (int)infos[0];
    return new LocalQuickFix[]{new DeleteCommentedOutCodeFix(), new UncommentCodeFix(),
      LocalQuickFix.from(new UpdateInspectionOptionFix(
        this, "minLines", InspectionGadgetsBundle.message("inspection.commented.out.code.disable.short.fragments"), lines + 1)),
    };
  }

  private static class DeleteCommentedOutCodeFix extends PsiUpdateModCommandQuickFix {

    private DeleteCommentedOutCodeFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("commented.out.code.delete.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiComment comment)) {
        return;
      }
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final List<PsiElement> toDelete = new ArrayList<>();
        toDelete.add(comment);
        PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
        while (sibling instanceof PsiComment && ((PsiComment)sibling).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          toDelete.add(sibling);
          sibling = PsiTreeUtil.skipWhitespacesForward(sibling);
        }
        toDelete.forEach(PsiElement::delete);
      }
      else {
        element.delete();
      }
    }
  }

  private static class UncommentCodeFix extends PsiUpdateModCommandQuickFix {

    private UncommentCodeFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("commented.out.code.uncomment.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiComment comment)) {
        return;
      }
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final List<TextRange> ranges = new ArrayList<>();
        ranges.add(comment.getTextRange());
        PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
        while (sibling instanceof PsiComment && ((PsiComment)sibling).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          ranges.add(sibling.getTextRange());
          sibling = PsiTreeUtil.skipWhitespacesForward(sibling);
        }
        final PsiFile file = element.getContainingFile();
        final Document document = file.getFileDocument();
        Collections.reverse(ranges);
        ranges.forEach(r -> document.deleteString(r.getStartOffset(), r.getStartOffset() + 2));
      }
      else {
        final TextRange range = element.getTextRange();
        final PsiFile file = element.getContainingFile();
        final Document document = file.getFileDocument();
        final int start = range.getStartOffset();
        final int end = range.getEndOffset();
        document.deleteString(end - 2, end);
        document.deleteString(start, start + 2);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CommentedOutCodeVisitor();
  }

  private class CommentedOutCodeVisitor extends BaseInspectionVisitor {

    private CommentedOutCodeVisitor() {}

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      if (comment instanceof PsiDocComment) {
        return;
      }
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final PsiElement before = PsiTreeUtil.skipWhitespacesBackward(comment);
        if (before instanceof PsiComment && ((PsiComment)before).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          return;
        }
        while (true) {
          final String text = getCommentText(comment);
          final int lines = StringUtil.countNewLines(text) + 1;
          if (lines < minLines) {
            return;
          }
          final ThreeState code = isCode(text, comment);
          if (code == ThreeState.YES) {
            registerErrorAtOffset(comment, 0, 2, lines);
            return;
          }
          else if (code == ThreeState.NO) {
            return;
          }
          final PsiElement after = PsiTreeUtil.skipWhitespacesForward(comment);
          if (!(after instanceof PsiComment && ((PsiComment)after).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT)) {
            break;
          }
          comment = (PsiComment)after;
        }
      }
      else {
        final String text = getCommentText(comment);
        final int lines = StringUtil.countNewLines(text) + 1;
        if (lines < minLines || isCode(text, comment) != ThreeState.YES) {
          return;
        }
        registerErrorAtOffset(comment, 0, 2, lines);
      }
    }
  }


  private static ThreeState isCode(String text, PsiElement context) {
    if (text.isEmpty()) {
      return ThreeState.NO;
    }
    final Project project = context.getProject();
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final PsiElement fragment;
    PsiElement parent = context.getParent();
    if (parent instanceof PsiMethod method) {
      if (!MethodUtils.isInsideMethodBody(context, method)) {
        parent = method.getParent();
      }
    }
    else if (parent instanceof PsiField) {
      parent = parent.getParent();
    }
    else if (parent instanceof PsiClass aClass) {
      if (!ClassUtils.isInsideClassBody(context, aClass)) {
        parent = aClass.getParent();
      }
    }
    if (parent instanceof PsiJavaFile) {
      fragment = PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", JavaFileType.INSTANCE, text);
    }
    else if (parent instanceof PsiClass) {
      fragment = factory.createMemberCodeFragment(text, context, false);
    }
    else {
      fragment = factory.createCodeBlockCodeFragment(text, context, false);
    }
    final boolean allowDanglingElse = isIfStatementWithoutElse(PsiTreeUtil.getPrevSiblingOfType(context, PsiStatement.class));
    if (!isInvalidCode(fragment, allowDanglingElse)) {
      return ThreeState.YES;
    }
    else if (PsiTreeUtil.getDeepestLast(fragment) instanceof PsiErrorElement) {
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  private static boolean isIfStatementWithoutElse(PsiStatement statement) {
    if (!(statement instanceof PsiIfStatement ifStatement)) {
      return false;
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch == null || isIfStatementWithoutElse(elseBranch);
  }

  private static String getCommentText(PsiComment comment) {
    String lineText = getEndOfLineCommentText(comment);
    if (lineText != null) {
      final StringBuilder result = new StringBuilder();
      while (lineText != null) {
        result.append(lineText).append('\n');
        final PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
        if (!(sibling instanceof PsiComment)) {
          break;
        }
        comment = (PsiComment)sibling;
        lineText = getEndOfLineCommentText(comment);
      }
      return result.toString().trim();
    }
    final String text = comment.getText();
    return StringUtil.trimEnd(StringUtil.trimStart(text, "/*"), "*/").trim();
  }

  @Nullable
  private static String getEndOfLineCommentText(PsiComment comment) {
    return (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) ? StringUtil.trimStart(comment.getText(), "//") : null;
  }

  private static boolean isInvalidCode(PsiElement element, boolean allowDanglingElse) {
    final PsiElement firstChild = element.getFirstChild();
    final PsiElement lastChild = element.getLastChild();
    final boolean strict = firstChild == lastChild && firstChild instanceof PsiExpressionStatement;
    if (firstChild instanceof PsiComment) {
      if (firstChild == lastChild) {
        return true;
      }
      final PsiElement sibling = firstChild.getNextSibling();
      if (sibling instanceof PsiWhiteSpace && sibling == lastChild) {
        return true;
      }
    }
    final CodeVisitor visitor = new CodeVisitor(strict, allowDanglingElse);
    element.accept(visitor);
    return visitor.isInvalidCode();
  }

  private static class CodeVisitor extends JavaRecursiveElementWalkingVisitor {
    private final boolean myStrict;
    private final boolean myAllowDanglingElse;
    private boolean invalidCode = false;
    private boolean codeFound = false;

    private CodeVisitor(boolean strict, boolean allowDanglingElse) {
      myStrict = strict;
      myAllowDanglingElse = allowDanglingElse;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      if (!(element instanceof PsiFile)) {
        codeFound = true;
      }
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {}

    @Override
    public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {}

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      if (myAllowDanglingElse && !codeFound && JavaPsiBundle.message("else.without.if").equals(element.getErrorDescription())) {
        return;
      }
      invalidCode = true;
      stopWalking();
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      if (PsiLiteralUtil.isUnsafeLiteral(expression)) {
        invalidCode = true;
        stopWalking();
      }
      else if (expression.getParent() instanceof PsiExpressionStatement) {
        invalidCode = true;
        stopWalking();
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getParent() instanceof PsiExpressionStatement) {
        invalidCode = true;
        stopWalking();
      }
    }

    @Override
    public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
      super.visitLabeledStatement(statement);
      if (isProbablyUrl(statement)) {
        invalidCode = true;
        stopWalking();
      }
    }

    private static boolean isProbablyUrl(PsiLabeledStatement statement) {
      if (statement.getStatement() == null) {
        return true;
      }
      final PsiIdentifier identifier = statement.getLabelIdentifier();
      final PsiElement sibling = identifier.getNextSibling();
      if (!PsiUtil.isJavaToken(sibling, JavaTokenType.COLON)) {
        return false;
      }
      PsiElement element = sibling.getNextSibling();
      return element instanceof PsiComment && ((PsiComment)element).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (myStrict && expression.getParent() instanceof PsiExpressionStatement) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (methodExpression.getQualifierExpression() == null && expression.resolveMethod() == null) {
          invalidCode = true;
          stopWalking();
        }
      }
    }

    public boolean isInvalidCode() {
      return !codeFound || invalidCode;
    }
  }
}
