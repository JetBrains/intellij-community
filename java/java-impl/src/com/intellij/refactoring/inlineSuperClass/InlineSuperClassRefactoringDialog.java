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
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class InlineSuperClassRefactoringDialog extends InlineOptionsDialog {
  private final PsiClass mySuperClass;
  private final PsiClass myCurrentInheritor;
  private final DocCommentPanel myDocPanel;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, PsiClass currentInheritor) {
    super(project, false, superClass);
    mySuperClass = superClass;
    myCurrentInheritor = currentInheritor;
    myInvokedOnReference = currentInheritor != null;
    myDocPanel = new DocCommentPanel("JavaDoc for inlined members");
    myDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    init();
    setTitle(InlineSuperClassRefactoringHandler.REFACTORING_NAME);
  }

  protected void doAction() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_SUPER_CLASS_THIS = isInlineThisOnly();
    }
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), isInlineThisOnly() ? myCurrentInheritor : null, mySuperClass, myDocPanel.getPolicy()));
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "Inline_Super_Class";
  }

  @NotNull
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             JBUI.emptyInsets(), 0, 0);
    panel.add(myDocPanel, gc);
    panel.add(super.createCenterPanel(), gc);
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    panel.add(Box.createVerticalGlue(), gc);
    return panel;
  }

  @Override
  protected String getNameLabelText() {
    return "Class " + mySuperClass.getQualifiedName();
  }

  @Override
  protected String getBorderTitle() {
    return "Inline";
  }

  @Override
  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.super.class");
  }

  @Override
  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.super.class");
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_SUPER_CLASS_THIS;
  }
}