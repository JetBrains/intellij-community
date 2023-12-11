// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.whileloop;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.TrackingEquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.psi.JavaTokenType.WHILE_KEYWORD;

public final class WhileCanBeDoWhileInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
        final PsiStatement body = statement.getBody();
        final PsiExpression condition = statement.getCondition();
        if (condition == null) return;

        PsiElement highlightElement = statement.getFirstChild();
        while (highlightElement != null && highlightElement.getNode().getElementType() != WHILE_KEYWORD) {
          highlightElement = highlightElement.getNextSibling();
        }
        highlightElement = highlightElement != null ? highlightElement : condition;

        final DiffRange duplicateElements;
        if (body != null) {
          final Block bodyBlock = Block.init(body);
          duplicateElements = bodyBlock.getDiffRange(Block.find(statement, bodyBlock.statements.size()));
        }
        else {
          duplicateElements = null;
        }
        if (duplicateElements != null) {
          holder.registerProblem(highlightElement,
                                 InspectionGadgetsBundle.message("inspection.while.can.be.replaced.with.do.while.message"),
                                 duplicateElements.type(), new ReplaceWhileWithDoWhileFix());
        }
        else if (isOnTheFly){
          holder.registerProblem(highlightElement,
                                 InspectionGadgetsBundle.message("inspection.while.can.be.replaced.with.do.while.message"),
                                 ProblemHighlightType.INFORMATION, new ReplaceWhileWithDoWhileFix());
        }
      }
    };
  }

  private static class ReplaceWhileWithDoWhileFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "inspection.while.can.be.replaced.with.do.while.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiWhileStatement statement = (PsiWhileStatement)element.getParent();
      if (statement == null) return;

      final PsiStatement body = statement.getBody();
      final PsiExpression condition = statement.getCondition();
      final boolean infiniteLoop = BoolUtils.isTrue(condition);

      final DiffRange duplicateElements;
      if (body != null) {
        final Block bodyBlock = Block.init(body);
        duplicateElements = bodyBlock.getDiffRange(Block.find(statement, bodyBlock.statements.size()));
      }
      else {
        duplicateElements = null;
      }
      final StringBuilder result = new StringBuilder();
      final CommentTracker tracker = new CommentTracker();
      if (!infiniteLoop && duplicateElements == null) {
        result.append("if(");
        if (condition != null) {
          result.append(tracker.text(condition));
        }
        result.append(") {\n");
      }
      if (body instanceof PsiBlockStatement blockStatement) {
        result.append("do {");
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiElement[] children = codeBlock.getChildren();
        if (children.length > 2) {
          for (int i = 1; i < children.length - 1; i++) {
            final PsiElement child = children[i];
            result.append(tracker.text(child));
          }
        }
        result.append('}');
      }
      else if (body != null) {
        result.append("do ").append(tracker.text(body)).append('\n');
      }
      result.append("while(");
      if (condition != null) {
        result.append(tracker.text(condition));
      }
      result.append(");");
      if (!infiniteLoop && duplicateElements == null) {
        result.append("\n}");
      }

      if (duplicateElements != null) {
        statement.getParent().deleteChildRange(duplicateElements.first(), duplicateElements.last());
      }
      PsiReplacementUtil.replaceStatement(statement, result.toString(), tracker);
    }
  }

  private record DiffRange(@NotNull PsiElement first, @NotNull PsiElement last, @NotNull ProblemHighlightType type) {
  }

  private static class Block {
    private @NotNull final List<PsiStatement> statements;
    private @NotNull final List<PsiComment> comments;
    private final @Nullable PsiStatement block;

    Block(@NotNull PsiBlockStatement block) {
      this.block = block;
      comments = new ArrayList<>();
      statements = new ArrayList<>();
      for (PsiElement element : block.getCodeBlock().getChildren()) {
        if (element instanceof PsiComment comment) {
          comments.add(comment);
        }
        else if (element instanceof PsiStatement statement) {
          statements.add(statement);
        }
      }
    }

    Block(@NotNull List<PsiStatement> statements, @NotNull List<PsiComment> comments) {
      this.statements = statements;
      this.comments = comments;
      this.block = null;
    }

    static Block init(@NotNull PsiStatement body) {
      if (body instanceof PsiBlockStatement block) {
        return new Block(block);
      }
      else {
        return new Block(Collections.singletonList(body), Collections.emptyList());
      }
    }

    static Block find(@NotNull PsiElement start, int maxSize) {
      final List<PsiStatement> statements = new ArrayList<>(maxSize);
      final List<PsiComment> comments = new ArrayList<>();
      while (statements.size() < maxSize && (start = start.getPrevSibling()) != null) {
        if (start instanceof PsiStatement statement) {
          if (statements.isEmpty() && statement instanceof PsiBlockStatement block) {
            return new Block(block);
          }
          else {
            statements.add(statement);
          }
        }
        else if (!statements.isEmpty() && start instanceof PsiComment comment) { // ignore comments between while and duplicate code
          comments.add(comment);
        }
      }
      Collections.reverse(statements);
      Collections.reverse(comments);
      return new Block(statements, comments);
    }

    @Nullable
    DiffRange getDiffRange(@NotNull Block block) {
      final EquivalenceChecker checker = new BreakTrackingEquivalenceChecker();
      if (block.block != null && this.block != null) {
        if (checker.statementsAreEquivalent(block.block, this.block)) {
          return new DiffRange(block.block, block.block, equalsComments(comments, block.comments)
                                                         ? ProblemHighlightType.WEAK_WARNING
                                                         : ProblemHighlightType.INFORMATION);
        }
        else {
          return null;
        }
      }
      else {
        if (statements.isEmpty()) return null;
        if (statements.size() != block.statements.size()) return null;
        for (int i = 0; i < statements.size(); i++) {
          if (!checker.statementsAreEquivalent(statements.get(i), block.statements.get(i))) {
            return null;
          }
        }
        return new DiffRange(block.statements.get(0), block.statements.get(block.statements.size() - 1),
                             equalsComments(comments, block.comments)
                             ? ProblemHighlightType.WEAK_WARNING
                             : ProblemHighlightType.INFORMATION);
      }
    }

    private static boolean equalsComments(@NotNull List<PsiComment> comments1, @NotNull List<PsiComment> comments2) {
      if (comments1.size() != comments2.size()) return false;
      for (int i = 0; i < comments1.size(); i++) {
        if (!Objects.equals(comments1.get(i).getText().trim(),
                            comments2.get(i).getText().trim())) {
          return false;
        }
      }
      return true;
    }

    /**
     * Expanding the possibilities of comparing break statements to avoid the situation:
     * <pre><code>
     *   while(a) {
     *     if(foo()) break;
     *     while(b) {
     *       if(foo()) break;
     *     }
     *   }
     * </code></pre>
     * it isn't equivalent to:
     * <pre><code>
     *   do(a) {
     *     if(foo()) break;
     *   }
     * </code></pre>
     */
    private static class BreakTrackingEquivalenceChecker extends TrackingEquivalenceChecker {
      @Override
      protected Match breakStatementsMatch(@NotNull PsiBreakStatement statement1, @NotNull PsiBreakStatement statement2) {
        return !isParentFor(statement1.findExitedStatement(), statement2.findExitedStatement())
               ? super.breakStatementsMatch(statement1, statement2)
               : EXACT_MISMATCH;
      }

      private static boolean isParentFor(@Nullable PsiElement element1, @Nullable PsiElement element2) {
        if (element1 == null || element2 == null) return true;
        if (element1.equals(element2)) return false;

        PsiElement pinedElement1 = element1;
        while ((element1 = element1.getParent()) != null) {
          if (element1.equals(element2)) return true;
        }
        while ((element2 = element2.getParent()) != null) {
          if (pinedElement1.equals(element2)) return true;
        }
        return false;
      }
    }
  }
}
