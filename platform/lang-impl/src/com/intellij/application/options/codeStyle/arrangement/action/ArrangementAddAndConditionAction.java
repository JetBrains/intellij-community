/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsUtil;
import com.intellij.application.options.codeStyle.arrangement.editor.ArrangementNodeEditor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/13/12 11:52 AM
 */
public class ArrangementAddAndConditionAction extends AnAction {

  public ArrangementAddAndConditionAction() {
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.add.and.node.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.add.and.node.description"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Map<ArrangementSettingType, List<?>> availableSettings = ArrangementSettingsUtil.buildAvailableOptions(e.getDataContext());
    if (availableSettings.isEmpty()) {
      return;
    }
    ArrangementNodeDisplayManager displayManager = ArrangementSettingsUtil.DISPLAY_MANAGER.getData(e.getDataContext());
    if (displayManager == null) {
      return;
    }

    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    JComponent tree = ArrangementSettingsUtil.TREE.getData(e.getDataContext());
    if (tree == null) {
      return;
    }

    ArrangementSettingsNode node = ArrangementSettingsUtil.getSettingsNode(e.getDataContext());
    
    if (node == null) {
      // TODO den implement
    }
    else {
      ArrangementNodeEditor editor = new ArrangementNodeEditor(displayManager, availableSettings);
      editor.applyColorsFrom(tree);
      Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(editor).setDisposable(project).setHideOnClickOutside(true)
        .setFillColor(tree.getBackground()).createBalloon();
      Point point = MouseInfo.getPointerInfo().getLocation();
      SwingUtilities.convertPointFromScreen(point, tree);
      balloon.show(new RelativePoint(tree, point), Balloon.Position.below);
    }
  }
}
