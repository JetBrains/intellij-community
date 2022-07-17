// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.HelpID;
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
  private final List<? extends PsiClass> mySuperClasses;

  private JList<PsiClass> mySuperClassesList;
  private final JCheckBox myCbReplaceInstanceOf = new JCheckBox();

  TurnRefsToSuperDialog(Project project, @NotNull PsiClass subClass, List<? extends PsiClass> superClasses) {
    super(project, true);

    mySubClass = subClass;
    mySuperClasses = superClasses;

    setTitle(TurnRefsToSuperHandler.getRefactoringName());
    init();
  }

  @Nullable
  public PsiClass getSuperClass() {
    return mySuperClassesList != null ? mySuperClassesList.getSelectedValue() : null;
  }

  public boolean isUseInInstanceOf() {
    return myCbReplaceInstanceOf.isSelected();
  }

  @Override
  protected String getHelpId() {
    return HelpID.TURN_REFS_TO_SUPER;
  }

  @Override
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
    classListLabel.setText(JavaRefactoringBundle.message("turnRefsToSuper.change.usages.to", mySubClass.getQualifiedName()));

    PsiClass nearestBase = RefactoringHierarchyUtil.getNearestBaseClass(mySubClass, true);
    int indexToSelect = 0;
    if(nearestBase != null) {
      indexToSelect = mySuperClasses.indexOf(nearestBase);
    }
    mySuperClassesList.setSelectedIndex(indexToSelect);
    panel.add(ScrollPaneFactory.createScrollPane(mySuperClassesList), BorderLayout.CENTER);

    myCbReplaceInstanceOf.setText(JavaRefactoringBundle.message("turnRefsToSuper.use.superclass.in.instanceof"));
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