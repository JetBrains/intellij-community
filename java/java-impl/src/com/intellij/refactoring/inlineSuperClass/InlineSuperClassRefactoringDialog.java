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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class InlineSuperClassRefactoringDialog extends InlineOptionsDialog {
  private final PsiClass mySuperClass;
  private final PsiClass myCurrentInheritor;
  private final PsiClass[] myTargetClasses;
  private final DocCommentPanel myDocPanel;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, PsiClass currentInheritor, final PsiClass... targetClasses) {
    super(project, false, superClass);
    mySuperClass = superClass;
    myCurrentInheritor = currentInheritor;
    myInvokedOnReference = currentInheritor != null;
    myTargetClasses = targetClasses;
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
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), isInlineThisOnly() ? myCurrentInheritor : null, mySuperClass, myDocPanel.getPolicy(), myTargetClasses));
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

  protected JComponent createCenterPanel() {
    final JLabel label = new JLabel("<html>Super class \'" +
                                     mySuperClass.getQualifiedName() +
                                     "\' inheritors: " +
                                     (myTargetClasses.length > 1 ? " <br>&nbsp;&nbsp;&nbsp;\'" : "\'") +
                                     StringUtil.join(myTargetClasses, new Function<PsiClass, String>() {
                                       public String fun(final PsiClass psiClass) {
                                         return psiClass.getQualifiedName();
                                       }
                                     }, "\',<br>&nbsp;&nbsp;&nbsp;\'") +
                                     "\'</html>");
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(0, 0, 0, 0), 0, 0);
    panel.add(myDocPanel, gc);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(label);
    pane.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    pane.setMinimumSize(JBDimension.create(new Dimension(-1, 100)));
    pane.setMaximumSize(JBDimension.create(new Dimension(-1, 400)));
    panel.add(pane, gc);
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    panel.add(super.createCenterPanel(), gc);
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