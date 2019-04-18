// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.intention.impl.singlereturn.ConvertToSingleReturnAction;
import com.intellij.codeInsight.intention.impl.singlereturn.FinishMarker;
import com.intellij.openapi.progress.EmptyProgressIndicator;
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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class InlineTransformer {
  /**
   * @param method method to check
   * @return true if given method could be inlined by this transformer (for some call sites)
   */
  public abstract boolean isMethodAccepted(PsiMethod method);

  /**
   * @param reference call site to check
   * @return true if inlining is possible at given call site
   */
  public abstract boolean isReferenceAccepted(PsiReference reference);

  /**
   * Transforms method body in the way so it can be inserted into the call site. May declare result variable if necessary.  
   * 
   * @param methodCopy non-physical copy of the method to be inlined (may be changed by this call)
   * @param returnType substituted method return type
   * @return result variable or null if unnecessary
   */
  public abstract PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType);

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
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType) {
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
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType) {
      return null;
    }
  }

  static class LoopContinueTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
        if (PsiTreeUtil.getParentOfType(statement, PsiLoopStatement.class, true, PsiMethod.class) != null) {
          // We cannot use "continue" without introducing a label if any of returns is inside nested loop.
          // Introducing a label is ugly, so let's move to the next transformer 
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return InlineUtil.getTailCallType(reference) == InlineUtil.TailCallType.Continue;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType) {
      extractReturnValues(methodCopy, true);
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
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType) {
      extractReturnValues(methodCopy, false);
      return null;
    }
  }

  private static void extractReturnValues(PsiMethod methodCopy, boolean replaceWithContinue) {
    PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(methodCopy);
    for (PsiReturnStatement returnStatement : returnStatements) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
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
      if (replaceWithContinue) {
        new CommentTracker().replaceAndRestoreComments(returnStatement, "continue;");
      }
    }
  }

  private static class ConvertToSingleReturnTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return true;
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return true;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiType returnType) {
      PsiCodeBlock block = Objects.requireNonNull(methodCopy.getBody());
      List<PsiReturnStatement> returns = Arrays.asList(PsiUtil.findReturnStatements(block));
      FinishMarker marker = FinishMarker.defineFinishMarker(block, returnType, returns);
      return ConvertToSingleReturnAction.convertReturns(methodCopy.getProject(), block, returnType, marker, returns.size(),
                                                        new EmptyProgressIndicator());
    }
  }

  private static List<InlineTransformer> getTransformers() {
    return ContainerUtil.immutableList(
      new TailCallTransformer(),
      new SimpleTailCallTransformer(),
      new NormalTransformer(),
      new LoopContinueTransformer(),
      new ConvertToSingleReturnTransformer()
    );
  }

  /**
   * Returns the most suitable transformer for given method and given call site
   * 
   * @param method method to inline (must have body)
   * @param reference call site where inlining should be performed
   * @return a transformer. Should always succeed as fallback transformer which accepts any method is available
   */
  @NotNull
  static InlineTransformer getSuitableTransformer(PsiMethod method, PsiReference reference) {
    for (InlineTransformer transformer : getTransformers()) {
      if (transformer.isMethodAccepted(method) && transformer.isReferenceAccepted(reference)) {
        return transformer;
      }
    }
    throw new InternalError("Transformer is unavailable");
  }
}
