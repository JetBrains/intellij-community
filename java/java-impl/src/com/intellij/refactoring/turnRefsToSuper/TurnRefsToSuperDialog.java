/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author dsl
 */
public class TurnRefsToSuperDialog extends RefactoringDialog {
  private final PsiClass mySubClass;
  private final List<PsiClass> mySuperClasses;

  private JList<PsiClass> mySuperClassesList;
  private final JCheckBox myCbReplaceInstanceOf = new JCheckBox();

  TurnRefsToSuperDialog(Project project, @NotNull PsiClass subClass, List<PsiClass> superClasses) {
    super(project, true);

    mySubClass = subClass;
    mySuperClasses = superClasses;

    setTitle(TurnRefsToSuperHandler.REFACTORING_NAME);
    init();
  }

  @Nullable
  public PsiClass getSuperClass() {
    return mySuperClassesList != null ? mySuperClassesList.getSelectedValue() : null;
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

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));

    final JLabel classListLabel = new JLabel();
    panel.add(classListLabel, BorderLayout.NORTH);

    mySuperClassesList = new JBList<>(mySuperClasses);
    mySuperClassesList.setCellRenderer(new ClassCellRenderer(mySuperClassesList.getCellRenderer()));
    mySuperClassesList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    classListLabel.setText(RefactoringBundle.message("turnRefsToSuper.change.usages.to", mySubClass.getQualifiedName()));

    PsiClass nearestBase = RefactoringHierarchyUtil.getNearestBaseClass(mySubClass, true);
    int indexToSelect = 0;
    if(nearestBase != null) {
      indexToSelect = mySuperClasses.indexOf(nearestBase);
    }
    mySuperClassesList.setSelectedIndex(indexToSelect);
    panel.add(ScrollPaneFactory.createScrollPane(mySuperClassesList), BorderLayout.CENTER);

    myCbReplaceInstanceOf.setText(RefactoringBundle.message("turnRefsToSuper.use.superclass.in.instanceof"));
    myCbReplaceInstanceOf.setSelected(false);
    myCbReplaceInstanceOf.setFocusable(false);
    panel.add(myCbReplaceInstanceOf, BorderLayout.SOUTH);

    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperDialog";
  }

  @Override
  protected void doAction() {
    JavaRefactoringSettings.getInstance().TURN_REFS_TO_SUPER_PREVIEW_USAGES = isPreviewUsages();
    final PsiClass superClass = getSuperClass();
    if (superClass != null) {
      invokeRefactoring(new TurnRefsToSuperProcessor(getProject(), mySubClass, superClass, isUseInInstanceOf()));
    }
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }
}