// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import com.siyeh.ig.psiutils.VariableNameGenerator;

import java.util.List;
import java.util.Objects;

public abstract class InlineTransformer {
  public abstract boolean isMethodAccepted(PsiMethod method);

  public abstract boolean isReferenceAccepted(PsiReference reference);

  public abstract PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference reference, PsiType returnType);

  static class NormalTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return !InlineMethodProcessor.checkBadReturns(method);
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return true;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference reference, PsiType returnType) {
      if (returnType == null || PsiType.VOID.equals(returnType)) return null;
      PsiCodeBlock block = Objects.requireNonNull(methodCopy.getBody());
      Project project = methodCopy.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String resultName = new VariableNameGenerator(block, VariableKind.LOCAL_VARIABLE).byName("result", "res").generate(true);
      PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(resultName, returnType, null);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      PsiLocalVariable resultVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      for (PsiReturnStatement returnStatement : PsiUtil.findReturnStatements(block)) {
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) continue;
        PsiStatement statement = factory.createStatementFromText(resultName + "=0;", null);
        statement = (PsiStatement)codeStyleManager.reformat(statement);
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)((PsiExpressionStatement)statement).getExpression();
        Objects.requireNonNull(assignment.getRExpression()).replace(returnValue);
        returnStatement.replace(statement);
      }
      return resultVar;
    }
  }

  static class TailCallTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return true;
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return InlineUtil.getTailCallType(reference) == InlineUtil.TailCallType.Return;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference reference, PsiType returnType) {
      return null;
    }
  }
  
  static class SimpleTailCallTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return true;
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return InlineUtil.getTailCallType(reference) == InlineUtil.TailCallType.Simple;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference reference, PsiType returnType) {
      PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(methodCopy);
      for (PsiReturnStatement returnStatement : returnStatements) {
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) continue;
        List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(returnValue);
        CommentTracker ct = new CommentTracker();
        sideEffects.forEach(ct::markUnchanged);
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, returnValue);
        ct.delete(returnValue);
        if (statements.length > 0) {
          PsiStatement lastAdded = BlockUtils.addBefore(returnStatement, statements);
          // Could be wrapped into {}, so returnStatement might be non-physical anymore
          returnStatement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiReturnStatement.class));
        }
        ct.insertCommentsBefore(returnStatement);
      }
      return null;
    }
  }

  static List<InlineTransformer> getTransformers() {
    return ContainerUtil.immutableList(
      new TailCallTransformer(),
      new SimpleTailCallTransformer(),
      new NormalTransformer()
    );
  }
  
  static InlineTransformer getSuitableTransformer(PsiMethod method, PsiReference reference) {
    for (InlineTransformer transformer : getTransformers()) {
      if (transformer.isMethodAccepted(method) && transformer.isReferenceAccepted(reference)) {
        return transformer;
      }
    }
    return null;
  }
}
