// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.List;

public class SingleStatementInBlockInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("single.statement.in.block.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SingleStatementInBlockVisitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      return new SingleStatementInBlockFix((String)infos[0]);
    }
    return null;
  }

  private static class SingleStatementInBlockVisitor extends ControlFlowStatementVisitorBase {

    @Contract("null->false")
    @Override
    protected boolean isApplicable(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)body).getCodeBlock();
        if (PsiUtilCore.hasErrorElementChild(codeBlock)) {
          return false;
        }
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1 && !(statements[0] instanceof PsiDeclarationStatement) && !isDanglingElseProblem(statements[0], body)) {
          if (PsiUtilCore.hasErrorElementChild(statements[0])) {
            return false;
          }
          final PsiFile file = body.getContainingFile();
          //this inspection doesn't work in JSP files, as it can't tell about tags
          // inside the braces
          if (!FileTypeUtils.isInServerPageFile(file)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    protected @Nullable Pair<PsiElement, PsiElement> getOmittedBodyBounds(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)body).getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          final PsiStatement statement = statements[0];
          if (statement.textContains('\n')) {
            return Pair.create(statement, statement);
          }
        }
      }
      return null;
    }

    /**
     * See JLS paragraphs 14.5, 14.9
     */
    private static boolean isDanglingElseProblem(@Nullable PsiStatement statement, @NotNull PsiStatement outerStatement) {
      return hasShortIf(statement) && hasPotentialDanglingElse(outerStatement);
    }

    private static boolean hasShortIf(@Nullable PsiStatement statement) {
      if (statement instanceof PsiIfStatement) {
        final PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
        return elseBranch == null || hasShortIf(elseBranch);
      }
      if (statement instanceof PsiLabeledStatement) {
        return hasShortIf(((PsiLabeledStatement)statement).getStatement());
      }
      if (statement instanceof PsiWhileStatement || statement instanceof PsiForStatement || statement instanceof PsiForeachStatement) {
        return hasShortIf(((PsiLoopStatement)statement).getBody());
      }
      return false;
    }

    private static boolean hasPotentialDanglingElse(@NotNull PsiStatement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement ifStatement) {
        if (ifStatement.getThenBranch() == statement && ifStatement.getElseBranch() != null) {
          return true;
        }
        return hasPotentialDanglingElse(ifStatement);
      }
      if (parent instanceof PsiLabeledStatement ||
          parent instanceof PsiWhileStatement ||
          parent instanceof PsiForStatement ||
          parent instanceof PsiForeachStatement) {
        return hasPotentialDanglingElse((PsiStatement)parent);
      }
      return false;
    }
  }

  private static class SingleStatementInBlockFix extends PsiUpdateModCommandQuickFix {
    private final @NonNls String myKeywordText;

    SingleStatementInBlockFix(String keywordText) {
      myKeywordText = keywordText;
    }

    @Override
    public @Nls @NotNull String getName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.quickfix", myKeywordText);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.family.quickfix");
    }

    @Override
    public @Unmodifiable @NotNull List<CustomizableIntentionAction.@NotNull RangeToHighlight> getRangesToHighlight(Project project,
                                                                                                                   ProblemDescriptor descriptor) {
      BlockData info = getBlockInfo(descriptor.getStartElement());
      if (info == null) return List.of();
      PsiCodeBlock block = info.block().getCodeBlock();
      return ContainerUtil.notNullize(Arrays.asList(
        CustomizableIntentionAction.RangeToHighlight.from(block.getLBrace(), EditorColors.DELETED_TEXT_ATTRIBUTES),
        CustomizableIntentionAction.RangeToHighlight.from(block.getRBrace(), EditorColors.DELETED_TEXT_ATTRIBUTES)));
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      BlockData info = getBlockInfo(startElement);
      if (info == null) return;
      final PsiStatement[] statements = info.block().getCodeBlock().getStatements();
      if (statements.length != 1) return;

      CommentTracker commentTracker = new CommentTracker();
      final String text = commentTracker.text(statements[0]);
      final PsiElement replacementExp = commentTracker.replace(info.block(), text);
      CodeStyleManager.getInstance(project).reformat(replacementExp);
      commentTracker.insertCommentsBefore(info.statement());
    }

    private @Nullable BlockData getBlockInfo(@NotNull PsiElement startElement) {
      PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(startElement, PsiStatement.class);
      if (statement instanceof PsiBlockStatement) {
        statement = PsiTreeUtil.getNonStrictParentOfType(statement.getParent(), PsiStatement.class);
      }
      final PsiElement body;
      if (statement instanceof PsiLoopStatement loopStatement) {
        body = loopStatement.getBody();
      }
      else if (statement instanceof PsiIfStatement ifStatement) {
        body = myKeywordText.equals("else") ? ifStatement.getElseBranch() : ifStatement.getThenBranch();
      }
      else {
        return null;
      }
      if (!(body instanceof PsiBlockStatement block)) return null;
      return new BlockData(statement, block);
    }

    private record BlockData(PsiStatement statement, PsiBlockStatement block) {
    }
  }
}
