/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Method;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * User: lex
 * Date: Oct 8, 2003
 * Time: 5:08:07 PM
 */
public class MethodReturnValueDescriptorImpl extends ValueDescriptorImpl{
  private final Method myMethod;
  private final Value myValue;

  public MethodReturnValueDescriptorImpl(Project project, @NotNull Method method, Value value) {
    super(project);
    myMethod = method;
    myValue = value;
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myValue;
  }

  @NotNull
  public Method getMethod() {
    return myMethod;
  }

  public String getName() {
    return NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myMethod.declaringType().name()) + "." +
           DebuggerUtilsEx.methodNameWithArguments(myMethod);
  }

  public Type getType() {
    if (myValue == null) {
      try {
        return myMethod.returnType();
      }
      catch (ClassNotLoadedException ignored) {
      }
    }
    return super.getType();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    return null;
  }

  public boolean canSetValue() {
    return false;
  }
}
