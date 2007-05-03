package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.ThisDescriptorImpl");  
  private final Value myValue;

  public ThisDescriptorImpl(Project project, Value value) {
    super(project);
    myValue = value;
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myValue;
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
