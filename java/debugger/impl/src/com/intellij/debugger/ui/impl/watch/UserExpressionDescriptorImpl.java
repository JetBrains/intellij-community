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

/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  public String getDeclaredType() {
    Value value = getValue();
    return value != null ? value.type().name() : null;
  }

  protected PsiCodeFragment getEvaluationCode(final StackFrameContext context) throws EvaluateException {
    Value value = myParentDescriptor.getValue();

    if(value instanceof ObjectReference) {
      final String typeName = value.type().name();

      final PsiClass psiClass = DebuggerUtils.findClass(myTypeName, myProject, context.getDebugProcess().getSearchScope());

      if (psiClass == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.type.name", typeName));
      }

      return createCodeFragment(psiClass);
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