// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeCook;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public class TypeCookDialog extends RefactoringDialog {
  private final PsiElement[] myElements;
  private final JLabel myClassNameLabel = new JLabel();
  private final JCheckBox myCbDropCasts = new JCheckBox();
  private final JCheckBox myCbPreserveRawArrays = new JCheckBox();
  private final JCheckBox myCbLeaveObjectParameterizedTypesRaw = new JCheckBox();
  private final JCheckBox myCbExhaustive = new JCheckBox();
  private final JCheckBox myCbCookObjects = new JCheckBox();
  private final JCheckBox myCbCookToWildcards = new JCheckBox();

  public TypeCookDialog(Project project, PsiElement[] elements) {
    super(project, true);

    setTitle(getRefactoringName());

    init();

    StringBuffer name = new StringBuffer();

    myElements = elements;
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      name.append(StringUtil.capitalize(UsageViewUtil.getType(element)));
      name.append(" ");
      name.append(DescriptiveNameUtil.getDescriptiveName(element));
      if (i < elements.length - 1) {
        name.append("<br>");
      }
    }

    myClassNameLabel.setText(XmlStringUtil.wrapInHtml(name));
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected String getHelpId() {
    return HelpID.TYPE_COOK;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    if (myCbDropCasts.isEnabled()) {
      myCbDropCasts.setSelected(JavaRefactoringSettings.getInstance().TYPE_COOK_DROP_CASTS);
    }

    if (myCbPreserveRawArrays.isEnabled()) {
      myCbPreserveRawArrays.setSelected(JavaRefactoringSettings.getInstance().TYPE_COOK_PRESERVE_RAW_ARRAYS);
    }

    if (myCbLeaveObjectParameterizedTypesRaw.isEnabled()) {
      myCbLeaveObjectParameterizedTypesRaw.setSelected(
        JavaRefactoringSettings.getInstance().TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW);
    }

    if (myCbExhaustive.isEnabled()) {
      myCbExhaustive.setSelected(
        JavaRefactoringSettings.getInstance().TYPE_COOK_EXHAUSTIVE);
    }

    if (myCbCookObjects.isEnabled()) {
      myCbCookObjects.setSelected(
        JavaRefactoringSettings.getInstance().TYPE_COOK_COOK_OBJECTS);
    }

    if (myCbCookToWildcards.isEnabled()) {
      myCbCookToWildcards.setSelected(
        JavaRefactoringSettings.getInstance().TYPE_COOK_PRODUCE_WILDCARDS);
    }

    myCbDropCasts.setText(JavaRefactoringBundle.message("type.cook.drop.obsolete.casts"));
    myCbPreserveRawArrays.setText(JavaRefactoringBundle.message("type.cook.preserve.raw.arrays"));
    myCbLeaveObjectParameterizedTypesRaw.setText(JavaRefactoringBundle.message("type.cook.leave.object.parameterized.types.raw"));
    myCbExhaustive.setText(JavaRefactoringBundle.message("type.cook.perform.exhaustive.search"));
    myCbCookObjects.setText(JavaRefactoringBundle.message("type.cook.generify.objects"));
    myCbCookToWildcards.setText(JavaRefactoringBundle.message("type.cook.produce.wildcard.types"));

    gbConstraints.insets = JBInsets.create(4, 8);

    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myClassNameLabel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridy = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myCbDropCasts, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myCbPreserveRawArrays, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.gridy = 2;
    optionsPanel.add(myCbLeaveObjectParameterizedTypesRaw, gbConstraints);

    gbConstraints.gridy++;
    optionsPanel.add(myCbExhaustive, gbConstraints);

    gbConstraints.gridy++;
    optionsPanel.add(myCbCookObjects, gbConstraints);

    gbConstraints.gridy++;
    optionsPanel.add(myCbCookToWildcards, gbConstraints);

    return optionsPanel;
  }

  @Override
  protected void doAction() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.TYPE_COOK_DROP_CASTS = myCbDropCasts.isSelected();
    settings.TYPE_COOK_PRESERVE_RAW_ARRAYS = myCbPreserveRawArrays.isSelected();
    settings.TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW = myCbLeaveObjectParameterizedTypesRaw.isSelected();
    settings.TYPE_COOK_EXHAUSTIVE = myCbExhaustive.isSelected();
    settings.TYPE_COOK_COOK_OBJECTS = myCbCookObjects.isSelected();
    settings.TYPE_COOK_PRODUCE_WILDCARDS = myCbCookToWildcards.isSelected();

    invokeRefactoring(new TypeCookProcessor(getProject(), myElements, getSettings()));
  }

  public Settings getSettings() {
    final boolean dropCasts = myCbDropCasts.isSelected();
    final boolean preserveRawArrays = true; //myCbPreserveRawArrays.isSelected();
    final boolean leaveObjectParameterizedTypesRaw = myCbLeaveObjectParameterizedTypesRaw.isSelected();
    final boolean exhaustive = myCbExhaustive.isSelected();
    final boolean cookObjects = myCbCookObjects.isSelected();
    final boolean cookToWildcards = myCbCookToWildcards.isSelected();

    return new Settings() {
      @Override
      public boolean dropObsoleteCasts() {
        return dropCasts;
      }

      @Override
      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }

      @Override
      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectParameterizedTypesRaw;
      }

      @Override
      public boolean exhaustive() {
        return exhaustive;
      }

      @Override
      public boolean cookObjects() {
        return cookObjects;
      }

      @Override
      public boolean cookToWildcards() {
        return cookToWildcards;
      }
    };
  }

  @Nls
  public static String getRefactoringName() {
    return JavaRefactoringBundle.message("generify.title");
  }
}