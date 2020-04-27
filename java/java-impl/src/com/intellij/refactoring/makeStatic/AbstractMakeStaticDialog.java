/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;

public abstract class AbstractMakeStaticDialog extends RefactoringDialog {
  protected final PsiTypeParameterListOwner myMember;
  protected final String myMemberName;

  public AbstractMakeStaticDialog(Project project, PsiTypeParameterListOwner member) {
    super(project, true);
    myMember = member;
    myMemberName = member.getName();
  }

  @Override
  protected void doAction() {
    if (!validateData())
      return;

    final Settings settings = new Settings(
            isReplaceUsages(),
            isMakeClassParameter() ? getClassParameterName() : null,
            getVariableData(),
            isGenerateDelegate()
    );
    if (myMember instanceof PsiMethod) {
      invokeRefactoring(new MakeMethodStaticProcessor(getProject(), (PsiMethod)myMember, settings));
    }
    else {
      invokeRefactoring(new MakeClassStaticProcessor(getProject(), (PsiClass)myMember, settings));
    }
  }

  protected boolean isGenerateDelegate() {
    return false;
  }

  protected abstract boolean validateData();

  public abstract boolean isMakeClassParameter();

  public abstract String getClassParameterName();

  public abstract VariableData[] getVariableData();

  public abstract boolean isReplaceUsages();

  protected JLabel createDescriptionLabel() {
    String type = UsageViewUtil.getType(myMember);
    return new JLabel(JavaRefactoringBundle.message("make.static.description.label", type, myMemberName));
  }
}
