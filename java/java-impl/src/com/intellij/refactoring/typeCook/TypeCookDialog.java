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
package com.intellij.refactoring.typeCook;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usageView.UsageViewUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 30.07.2003
 * Time: 21:36:29
 * To change this template use Options | File Templates.
 */
public class TypeCookDialog extends RefactoringDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("generify.title");

  private final PsiElement[] myElements;
  private final JLabel myClassNameLabel = new JLabel();
  private final JCheckBox myCbDropCasts = new JCheckBox();
  private final JCheckBox myCbPreserveRawArrays = new JCheckBox();
  private final JCheckBox myCbLeaveObjectParameterizedTypesRaw = new JCheckBox();
  private final JCheckBox myCbExhaustive = new JCheckBox();
  private final JCheckBox myCbCookObjects = new JCheckBox();
  private final JCheckBox myCbCookToWildcards = new JCheckBox();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public TypeCookDialog(Project project, PsiElement[] elements) {
    super(project, true);

    setTitle(REFACTORING_NAME);

    init();

    StringBuffer name = new StringBuffer("<html>");

    myElements = elements;
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      name.append(StringUtil.capitalize(UsageViewUtil.getType(element)));
      name.append(" ");
      name.append(UsageViewUtil.getDescriptiveName(element));
      if (i < elements.length - 1) {
        name.append("<br>");
      }
    }

    name.append("</html>");

    myClassNameLabel.setText(name.toString());
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.TYPE_COOK);
  }

  protected JComponent createNorthPanel() {
    JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    optionsPanel.setBorder(IdeBorderFactory.createRoundedBorder());

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

    myCbDropCasts.setText(RefactoringBundle.message("type.cook.drop.obsolete.casts"));
    myCbPreserveRawArrays.setText(RefactoringBundle.message("type.cook.preserve.raw.arrays"));
    myCbLeaveObjectParameterizedTypesRaw.setText(RefactoringBundle.message("type.cook.leave.object.parameterized.types.raw"));
    myCbExhaustive.setText(RefactoringBundle.message("type.cook.perform.exhaustive.search"));
    myCbCookObjects.setText(RefactoringBundle.message("type.cook.generify.objects"));
    myCbCookToWildcards.setText(RefactoringBundle.message("type.cook.produce.wildcard.types"));

    gbConstraints.insets = new Insets(4, 8, 4, 8);

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
      public boolean dropObsoleteCasts() {
        return dropCasts;
      }

      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }

      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectParameterizedTypesRaw;
      }

      public boolean exhaustive() {
        return exhaustive;
      }

      public boolean cookObjects() {
        return cookObjects;
      }

      public boolean cookToWildcards() {
        return cookToWildcards;
      }
    };
  }
}
