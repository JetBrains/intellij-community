/*
 * Class WatchItemDescriptor
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.Value;

/**
 * update(Value, boolean) method must be called whenever the state of the target VM changes
 */
public class WatchItemDescriptor extends EvaluationDescriptor {
  private boolean myAllowBreakpoints;

  public WatchItemDescriptor(Project project, TextWithImports text, boolean allowBreakpoints) {
    super(text, project);
    setValueLabel("");
    myAllowBreakpoints = allowBreakpoints;
  }

  public WatchItemDescriptor(Project project, TextWithImports text, Value value, boolean allowBreakpoints) {
    super(text, project, value);
    setValueLabel("");
    myAllowBreakpoints = allowBreakpoints;
  }

  public String getName() {
    return getEvaluationText().getText();
  }

  public void setNew() {
    myIsNew = true;
  }

  // call update() after setting a new expression
  public void setEvaluationText(TextWithImports evaluationText) {
    if (!Comparing.equal(getEvaluationText(), evaluationText)) {
      setLvalue(false);
    }
    myText = evaluationText;
    myIsNew = true;
    setValueLabel("");
  }

  protected EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext) {
    evaluationContext.setAllowBreakpoints(myAllowBreakpoints);
    return evaluationContext;
  }

  protected PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException {
    final PsiElement psiContext = PositionUtil.getContextElement(context);
    final PsiCodeFragment fragment =
      getEffectiveCodeFragmentFactory(psiContext).createCodeFragment(getEvaluationText(), psiContext, myProject);
    fragment.forceResolveScope(GlobalSearchScope.allScope(myProject));
    return fragment;
  }

  public void setAllowBreakpoints(boolean b) {
    myAllowBreakpoints = b;
  }
}