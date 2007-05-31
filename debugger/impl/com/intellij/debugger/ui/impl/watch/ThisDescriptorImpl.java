package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Oct 8, 2003
 * Time: 5:08:07 PM
 */
public class ThisDescriptorImpl extends ValueDescriptorImpl{

  public ThisDescriptorImpl(Project project) {
    super(project);
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return evaluationContext.getThisObject();
  }

  public String getName() {
    //noinspection HardCodedStringLiteral
    return "this"; 
  }

  public String calcValueName() {
    return getName();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = PsiManager.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }

  public boolean canSetValue() {
    return false;
  }
}
