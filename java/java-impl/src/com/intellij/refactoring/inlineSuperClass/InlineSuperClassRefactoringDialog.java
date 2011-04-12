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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InlineSuperClassRefactoringDialog extends RefactoringDialog{
  private final PsiClass mySuperClass;
  private final PsiClass[] myTargetClasses;
  private final DocCommentPanel myDocPanel;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, final PsiClass... targetClasses) {
    super(project, false);
    mySuperClass = superClass;
    myTargetClasses = targetClasses;
    myDocPanel = new DocCommentPanel("JavaDoc for inlined members");
    myDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    init();
    setTitle(InlineSuperClassRefactoringHandler.REFACTORING_NAME);
  }

  protected void doAction() {
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), mySuperClass, myDocPanel.getPolicy(), myTargetClasses));
  }

  @Override
  protected JComponent createNorthPanel() {
    return myDocPanel;
  }

  protected JComponent createCenterPanel() {
    final JLabel label = new JLabel("<html>Inline \'" +
                                     mySuperClass.getQualifiedName() +
                                     "\' to " +
                                     (myTargetClasses.length > 1 ? " <br>&nbsp;&nbsp;&nbsp;\'" : "\'") +
                                     StringUtil.join(myTargetClasses, new Function<PsiClass, String>() {
                                         public String fun(final PsiClass psiClass) {
                                           return psiClass.getQualifiedName();
                                         }
                                       }, "\',<br>&nbsp;&nbsp;&nbsp;\'") +
                                     "\'</html>");
    label.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    return label;
  }
}