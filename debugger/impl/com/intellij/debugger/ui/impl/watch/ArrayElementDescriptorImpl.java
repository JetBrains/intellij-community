package com.intellij.debugger.ui.impl.watch;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.tree.ArrayElementDescriptor;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.DebuggerContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import com.sun.jdi.ObjectCollectedException;
import com.sun.tools.corba.se.idl.constExpr.EvaluationException;

public class ArrayElementDescriptorImpl extends ValueDescriptorImpl implements ArrayElementDescriptor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl");

  private int myIndex;
  private ArrayReference myArray;

  public ArrayElementDescriptorImpl(Project project, ArrayReference array, int index) {
    super(project);
    myArray = array;
    myIndex = index;
    setLvalue(true);
  }

  public int getIndex() {
    return myIndex;
  }

  public ArrayReference getArray() {
    return myArray;
  }

  public String getName() {
    return String.valueOf(myIndex);
  }

  public String calcValueName() {
    return "[" + getName() + "]";
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    try {
      return myArray.getValue(myIndex);
    }
    catch (ObjectCollectedException e) {
      throw EvaluateExceptionUtil.ARRAY_WAS_COLLECTED;
    }
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
    PsiElementFactory elementFactory = PsiManager.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText("this[" + myIndex + "]", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }
}