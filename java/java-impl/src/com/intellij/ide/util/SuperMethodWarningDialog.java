/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

//TODO: review title and text!!!
class SuperMethodWarningDialog extends DialogWrapper {
  static final int NO_EXIT_CODE=NEXT_USER_EXIT_CODE+1;
  private final String myName;
  private final String[] myClassNames;
  private final String myActionString;
  private final boolean myIsSuperAbstract;
  private final boolean myIsParentInterface;
  private final boolean myIsContainedInInterface;

  SuperMethodWarningDialog(@NotNull Project project,
                           @NotNull String name,
                           @NotNull String actionString,
                           boolean isSuperAbstract,
                           boolean isParentInterface,
                           boolean isContainedInInterface,
                           @NotNull String... classNames) {
    super(project, true);
    myName = name;
    myClassNames = classNames;
    myActionString = actionString;
    myIsSuperAbstract = isSuperAbstract;
    myIsParentInterface = isParentInterface;
    myIsContainedInInterface = isContainedInInterface;
    setTitle(IdeBundle.message("title.warning"));
    setButtonsAlignment(SwingConstants.CENTER);
    setOKButtonText(CommonBundle.getYesButtonText());
    init();
  }

  @Override
  @NotNull
  protected Action[] createActions(){
    return new Action[]{getOKAction(),new NoAction(),getCancelAction()};
  }

  @Override
  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    JLabel iconLabel = new JLabel(Messages.getQuestionIcon());
    panel.add(iconLabel, BorderLayout.WEST);
    JPanel labelsPanel = new JPanel(new GridLayout(0, 1, 0, 0));
    labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    String classType = myIsParentInterface ? IdeBundle.message("element.of.interface") : IdeBundle.message("element.of.class");
    String methodString = IdeBundle.message("element.method");
    labelsPanel.add(new JLabel(IdeBundle.message("label.method", myName)));
    if (myClassNames.length == 1) {
      final String className = myClassNames[0];
      labelsPanel.add(new JLabel(myIsContainedInInterface || !myIsSuperAbstract
                                 ? IdeBundle.message("label.overrides.method.of_class_or_interface.name", methodString, classType, className)
                                 : IdeBundle.message("label.implements.method.of_class_or_interface.name", methodString, classType, className)));
    }
    else {
      final JLabel multLabel = new JLabel(IdeBundle.message("label.implements.method.of_interfaces"));
      multLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      labelsPanel.add(multLabel);
 
      for (final String className : myClassNames) {
        labelsPanel.add(new JLabel("    " + className));
      }
    }
 
    JLabel doYouWantLabel = new JLabel(IdeBundle.message("prompt.do.you.want.to.action_verb.the.method.from_class", myActionString, myClassNames.length));
    doYouWantLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
    labelsPanel.add(doYouWantLabel);
    panel.add(labelsPanel, BorderLayout.CENTER);
    return panel;
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  @Override
  public JComponent createCenterPanel() {
    return null;
  }

  private class NoAction extends AbstractAction {
    private NoAction() {
      super(CommonBundle.getNoButtonText());
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      close(NO_EXIT_CODE);
    }
  }
}

