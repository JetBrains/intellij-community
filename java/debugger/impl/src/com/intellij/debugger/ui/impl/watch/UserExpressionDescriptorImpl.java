/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiType;
import com.sun.jdi.Type;
import org.jetbrains.annotations.Nullable;

public class UserExpressionDescriptorImpl extends EvaluationDescriptor implements UserExpressionDescriptor{
  private final ValueDescriptorImpl myParentDescriptor;
  private final String myTypeName;
  private final String myName;
  private final int myEnumerationIndex;

  public UserExpressionDescriptorImpl(Project project,
                                      ValueDescriptorImpl parent,
                                      String typeName,
                                      String name,
                                      TextWithImports text,
                                      int enumerationIndex) {
    super(text, project);
    myParentDescriptor = parent;
    myTypeName = typeName;
    myName = name;
    myEnumerationIndex = enumerationIndex;
  }

  public String getName() {
    return StringUtil.isEmpty(myName) ? myText.getText() : myName;
  }

  @Nullable
  @Override
  public String getDeclaredType() {
    Type type = getType();
    return type != null ? type.name() : null;
  }

  protected PsiCodeFragment getEvaluationCode(final StackFrameContext context) throws EvaluateException {
    Pair<PsiClass, PsiType> psiClassAndType = DebuggerUtilsImpl.getPsiClassAndType(myTypeName, myProject);
    if (psiClassAndType.first == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.type.name", myTypeName));
    }
    PsiCodeFragment fragment = createCodeFragment(psiClassAndType.first);
    if (fragment instanceof JavaCodeFragment) {
      ((JavaCodeFragment)fragment).setThisType(psiClassAndType.second);
    }
    return fragment;
  }

  public ValueDescriptorImpl getParentDescriptor() {
    return myParentDescriptor;
  }

  protected EvaluationContextImpl getEvaluationContext(final EvaluationContextImpl evaluationContext) {
    return evaluationContext.createEvaluationContext(myParentDescriptor.getValue());
  }

  public int getEnumerationIndex() {
    return myEnumerationIndex;
  }
}