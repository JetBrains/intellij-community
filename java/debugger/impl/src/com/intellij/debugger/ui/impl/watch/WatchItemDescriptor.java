// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class WatchItemDescriptor
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiCodeFragment;
import com.sun.jdi.Value;

public class WatchItemDescriptor extends EvaluationDescriptor {
  public WatchItemDescriptor(Project project, TextWithImports text) {
    super(text, project);
    setValueLabel("");
  }

  public WatchItemDescriptor(Project project, TextWithImports text, Value value) {
    super(text, project, value);
    setValueLabel("");
  }

  public WatchItemDescriptor(Project project, TextWithImports text, Value value, EvaluationContextImpl evaluationContext) {
    super(text, project, value);
    setValueLabel("");
    myStoredEvaluationContext = evaluationContext;
  }

  @Override
  public String getName() {
    return getEvaluationText().getText();
  }

  public void setNew() {
    myIsNew = true;
  }

  public void setEvaluationText(TextWithImports evaluationText) {
    if (!Comparing.equal(getEvaluationText(), evaluationText)) {
      setLvalue(false);
    }
    myText = evaluationText;
    myIsNew = true;
    setValueLabel("");
  }

  @Override
  protected EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext) {
    return evaluationContext;
  }

  @Override
  protected PsiCodeFragment getEvaluationCode(StackFrameContext context) {
    return createCodeFragment(PositionUtil.getContextElement(context));
  }
}