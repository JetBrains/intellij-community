package org.jetbrains.debugger.memory.utils;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class InstanceValueDescriptor extends ValueDescriptorImpl {

  public InstanceValueDescriptor(Project project, Value value) {
    super(project, value);
  }

  @Override
  public String calcValueName() {
    ObjectReference ref = ((ObjectReference) getValue());
    if (ref instanceof ArrayReference) {
      ArrayReference arrayReference = (ArrayReference) ref;
      return NamesUtils.getArrayUniqueName(arrayReference);
    }
    return NamesUtils.getUniqueName(ref);
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getValue();
  }

  @Override
  public boolean isShowIdLabel() {
    return false;
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    ObjectReference ref = ((ObjectReference) getValue());
    String name = NamesUtils.getUniqueName(ref).replace("@", "");
    String presentation = String.format("%s_DebugLabel", name);

    return elementFactory.createExpressionFromText(presentation, ContextUtil.getContextElement(debuggerContext));
  }
}
