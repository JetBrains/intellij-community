// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class LightEditConfirmationDialog extends DialogWrapper {
  static final int STAY_IN_LIGHT_EDIT = 100;
  static final int PROCEED_TO_PROJECT = 101;

  private boolean myDontAskFlag;

  LightEditConfirmationDialog(@Nullable Project project) {
    super(project, false);
    setTitle(ApplicationBundle.message("light.edit.confirmation.dialog.title"));
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    DialogWrapperAction stayInLightEditAction = new DialogWrapperAction(ApplicationBundle.message("light.edit.confirmation.stay.button")) {
      @Override
      protected void doAction(ActionEvent e) {
        close(STAY_IN_LIGHT_EDIT);
      }
    };
    stayInLightEditAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
    stayInLightEditAction.putValue(DialogWrapper.FOCUSED_ACTION, true);
    actions.add(stayInLightEditAction);
    actions.add(
      new DialogWrapperAction(ApplicationBundle.message("light.edit.confirmation.project.button")) {
        @Override
        protected void doAction(ActionEvent e) {
          close(PROCEED_TO_PROJECT);
        }
      }
    );
    return actions.toArray(new Action[0]);
  }

  public boolean isDontAsk() {
    return myDontAskFlag;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel contentPane = new JPanel();
    contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.X_AXIS));
    JLabel iconLabel = new JLabel(UIUtil.getQuestionIcon());
    iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
    contentPane.add(iconLabel);
    contentPane.add(Box.createRigidArea(JBUI.size(10)));
    JPanel messagePane = new JPanel();
    messagePane.setLayout(new BoxLayout(messagePane, BoxLayout.Y_AXIS));
    messagePane.setAlignmentY(Component.TOP_ALIGNMENT);
    JBLabel titleLabel = new JBLabel(ApplicationBundle.message("light.edit.confirmation.content.title"));
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
    messagePane.add(titleLabel);
    messagePane.add(Box.createRigidArea(JBUI.size(10)));
    JBLabel textLabel = new JBLabel();
    textLabel.setAllowAutoWrapping(false);
    String text = ApplicationBundle.message("light.edit.confirmation.content.text");
    //noinspection HardCodedStringLiteral
    textLabel.setText(text.replaceAll("\\s", "&nbsp;"));
    messagePane.add(textLabel);
    messagePane.add(Box.createRigidArea(JBUI.size(10)));
    JCheckBox dontAskBox = new JCheckBox(ApplicationBundle.message("light.edit.confirmation.do.not.ask"));
    dontAskBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDontAskFlag = dontAskBox.isSelected();
      }
    });
    messagePane.add(dontAskBox);
    messagePane.add(Box.createRigidArea(JBUI.size(10)));
    contentPane.add(messagePane);
    contentPane.add(Box.createRigidArea(JBUI.size(20)));
    return contentPane;
  }
}
