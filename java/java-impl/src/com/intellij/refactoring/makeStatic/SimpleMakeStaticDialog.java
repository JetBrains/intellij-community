// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import java.awt.*;

public class SimpleMakeStaticDialog extends AbstractMakeStaticDialog {
  private JCheckBox myCbReplaceUsages;

  public SimpleMakeStaticDialog(Project project, PsiTypeParameterListOwner member) {
    super(project, member);
    String type = UsageViewUtil.getType(myMember);
    setTitle(JavaRefactoringBundle.message("make.0.static", StringUtil.capitalize(type)));
    init();
  }

  @Override
  protected boolean validateData() {
    return true;
  }

  @Override
  public boolean isMakeClassParameter() {
    return false;
  }

  @Override
  public String getClassParameterName() {
    return null;
  }

  @Override
  public VariableData[] getVariableData() {
    return null;
  }

  @Override
  public boolean isReplaceUsages() {
    return myCbReplaceUsages.isSelected();
  }

  @Override
  protected String getHelpId() {
    return HelpID.MAKE_METHOD_STATIC_SIMPLE;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBInsets.create(4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(createDescriptionLabel(), gbConstraints);

    gbConstraints.gridy++;
    myCbReplaceUsages = new JCheckBox(JavaRefactoringBundle.message("replace.instance.qualifiers.with.class.references"));
    panel.add(myCbReplaceUsages, gbConstraints);
    myCbReplaceUsages.setSelected(true);
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }
}