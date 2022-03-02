// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

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
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface InlineTransformer {
  
  /**
   * Transforms method body in the way so it can be inserted into the call site. May declare result variable if necessary.  
   * 
   * @param methodCopy non-physical copy of the method to be inlined (may be changed by this call)
   * @param callSite method call
   * @param returnType substituted method return type
   * @return result variable or null if unnecessary
   */
  PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference callSite, PsiType returnType);

  /**
   * @return true if this transformer is a fallback transformer which may significantly rewrite the method body
   */
  default boolean isFallBackTransformer() {
    return false;
  }

  class NormalTransformer implements InlineTransformer {

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference callSite, PsiType returnType) {
      if (returnType == null || PsiType.VOID.equals(returnType) ||
          callSite.getElement().getParent() instanceof PsiMethodCallExpression &&
          ExpressionUtils.isVoidContext((PsiExpression)callSite.getElement().getParent())) {
        
        InlineUtil.extractReturnValues(methodCopy, false);
        return null;
      }
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

  class ConvertToSingleReturnTransformer implements InlineTransformer {

    @Override
    public boolean isFallBackTransformer() {
      return true;
    }

    @Override
    public PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReference callSite, PsiType returnType) {
      if (callSite.getElement().getParent() instanceof PsiMethodCallExpression &&
          ExpressionUtils.isVoidContext((PsiExpression)callSite.getElement().getParent())) {
        
        InlineUtil.extractReturnValues(methodCopy, false);
        returnType = PsiType.VOID;
      }
      PsiCodeBlock block = Objects.requireNonNull(methodCopy.getBody());
      List<PsiReturnStatement> returns = Arrays.asList(PsiUtil.findReturnStatements(block));
      FinishMarker marker = FinishMarker.defineFinishMarker(block, returnType, returns);
      return ConvertToSingleReturnAction.convertReturns(methodCopy.getProject(), block, returnType, marker, returns.size(),
                                                        new EmptyProgressIndicator());
    }
  }

  /**
   * Returns the most suitable transformer for given method and given call site
   * 
   * @param method method to inline (must have body)
   * @return a function which produces a transformer for given reference to the inlined method. 
   *        Should always succeed as fallback transformer which accepts any method is available.
   */
  @NotNull
  static Function<PsiReference, InlineTransformer> getSuitableTransformer(PsiMethod method) {
    PsiReturnStatement[] returns = PsiUtil.findReturnStatements(method);
    PsiCodeBlock body = Objects.requireNonNull(method.getBody());
    if (!InlineMethodProcessor.checkBadReturns(returns, body)) {
      return ref -> {
        InlineUtil.TailCallType type = InlineUtil.getTailCallType(ref);
        if (type == InlineUtil.TailCallType.Return || type == InlineUtil.TailCallType.Simple) {
          return type.getTransformer();
        }
        return new NormalTransformer();
      };
    }
    boolean canUseContinue = Arrays.stream(returns).allMatch(statement -> {
      // We cannot use "continue" without introducing a label if any of returns is inside nested loop.
      // Introducing a label is ugly, so let's move to fallback transformer 
      return PsiTreeUtil.getParentOfType(statement, PsiLoopStatement.class, true, PsiMethod.class) == null;
    });
    BooleanReturnModel model = BooleanReturnModel.from(body, returns);
    return ref -> {
      InlineUtil.TailCallType type = InlineUtil.getTailCallType(ref);
      if (type == InlineUtil.TailCallType.Continue && !canUseContinue) {
        type = InlineUtil.TailCallType.None;
      }
      InlineTransformer fromTailCall = type.getTransformer();
      if (fromTailCall != null) {
        return fromTailCall;
      }
      if (model != null) {
        InlineTransformer fromBooleanModel = model.getTransformer(ref);
        if (fromBooleanModel != null) {
          return fromBooleanModel;
        }
      }
      return new ConvertToSingleReturnTransformer();
    };
  }
}
