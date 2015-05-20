/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * User: lex
 * Date: Oct 8, 2003
 * Time: 5:08:07 PM
 */
public class ThrownExceptionValueDescriptorImpl extends ValueDescriptorImpl{
  @NotNull
  private final ObjectReference myExceptionObj;

  public ThrownExceptionValueDescriptorImpl(Project project, @NotNull ObjectReference exceptionObj) {
    super(project);
    myExceptionObj = exceptionObj;
    // deliberately force default renderer as it does not invoke methods for rendering
    // calling methods on exception object at this moment may lead to VM hang
    setRenderer(DebugProcessImpl.getDefaultRenderer(exceptionObj));
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myExceptionObj;
  }

  public String getName() {
    return "Exception";
  }

  @NotNull
  @Override
  public Type getType() {
    return myExceptionObj.referenceType();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    throw new EvaluateException("Evaluation not supported for thrown exception object");
  }

  public boolean canSetValue() {
    return false;
  }
}
