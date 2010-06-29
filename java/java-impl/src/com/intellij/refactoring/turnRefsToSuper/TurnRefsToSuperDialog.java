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
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.06.2002
 * Time: 11:30:13
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TurnRefsToSuperDialog extends RefactoringDialog {
  @NotNull private final PsiClass mySubClass;
  private final List mySuperClasses;

  private JList mySuperClassesList = null;
  private final JCheckBox myCbReplaceInstanceOf = new JCheckBox();

  TurnRefsToSuperDialog(Project project, @NotNull PsiClass subClass, List superClasses) {
    super(project, true);

    mySubClass = subClass;
    mySuperClasses = superClasses;

    setTitle(TurnRefsToSuperHandler.REFACTORING_NAME);
    init();
  }

  public PsiClass getSuperClass() {
    if(mySuperClassesList != null) {
      return (PsiClass) mySuperClassesList.getSelectedValue();
    }
    else {
      return null;
    }
  }

  public boolean isUseInInstanceOf() {
    return myCbReplaceInstanceOf.isSelected();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.TURN_REFS_TO_SUPER);
  }

  public JComponent getPreferredFocusedComponent() {
    return mySuperClassesList;
  }


  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createBorder());

    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final JLabel classListLabel = new JLabel();
    panel.add(classListLabel, gbConstraints);

    mySuperClassesList = new JBList(mySuperClasses.toArray());
    mySuperClassesList.setCellRenderer(new ClassCellRenderer());
    mySuperClassesList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    classListLabel.setText(RefactoringBundle.message("turnRefsToSuper.change.usages.to", mySubClass.getQualifiedName()));

    PsiClass nearestBase = RefactoringHierarchyUtil.getNearestBaseClass(mySubClass, true);
    int indexToSelect = 0;
    if(nearestBase != null) {
      indexToSelect = mySuperClasses.indexOf(nearestBase);
    }
    mySuperClassesList.setSelectedIndex(indexToSelect);
    gbConstraints.gridy++;
    panel.add(new JScrollPane(mySuperClassesList), gbConstraints);

    gbConstraints.gridy++;
    myCbReplaceInstanceOf.setText(RefactoringBundle.message("turnRefsToSuper.use.superclass.in.instanceof"));
    myCbReplaceInstanceOf.setSelected(false);
    myCbReplaceInstanceOf.setFocusable(false);
    panel.add(myCbReplaceInstanceOf, gbConstraints);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperDialog";
  }

  protected void doAction() {
    JavaRefactoringSettings.getInstance().TURN_REFS_TO_SUPER_PREVIEW_USAGES = isPreviewUsages();
    final TurnRefsToSuperProcessor processor = new TurnRefsToSuperProcessor(
      getProject(), mySubClass, getSuperClass(), isUseInInstanceOf());
    invokeRefactoring(processor);
  }

  protected JComponent createCenterPanel() {
    return null;
  }
}
