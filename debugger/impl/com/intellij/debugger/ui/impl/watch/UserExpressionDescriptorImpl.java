/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class UserExpressionDescriptorImpl extends EvaluationDescriptor implements UserExpressionDescriptor{
  private final ValueDescriptorImpl myParentDescriptor;
  private final String myTypeName;
  private final String myName;

  public UserExpressionDescriptorImpl(Project project, ValueDescriptorImpl parent, String typeName, String name, TextWithImports text) {
    super(text, project);
    myParentDescriptor = parent;
    myTypeName = typeName;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public String calcValueName() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(getName());
    buffer.append(": ");
    if(getValue() != null) buffer.append(getValue().type().name());

    return buffer.toString();
  }

  protected PsiCodeFragment getEvaluationCode(final StackFrameContext context) throws EvaluateException {
    Value value = myParentDescriptor.getValue();

    if(value instanceof ObjectReference) {
      final String typeName = value.type().name();

      final PsiClass psiClass = DebuggerUtilsEx.findClass(myTypeName, myProject, ((DebugProcessImpl)context.getDebugProcess()).getSession().getSearchScope());

      if (psiClass == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.type.name", typeName));
      }

      final PsiCodeFragment fragment =
        getEffectiveCodeFragmentFactory(psiClass).createCodeFragment(getEvaluationText(), psiClass, myProject);
      fragment.forceResolveScope(GlobalSearchScope.allScope(myProject));
      return fragment;
    }
    else {
      throw EvaluateExceptionUtil.createEvaluateException(
        DebuggerBundle.message("evaluation.error.objref.expected", myParentDescriptor.getName())
      );
    }
  }

  public ValueDescriptorImpl getParentDescriptor() {
    return myParentDescriptor;
  }

  protected EvaluationContextImpl getEvaluationContext(final EvaluationContextImpl evaluationContext) {
    return evaluationContext.createEvaluationContext(myParentDescriptor.getValue());
  }
}