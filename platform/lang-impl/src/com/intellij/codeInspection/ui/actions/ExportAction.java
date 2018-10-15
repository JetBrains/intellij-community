// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class ExportAction extends AnAction implements DumbAware {

  public ExportAction() {
    super(InspectionsBundle.message("inspection.action.export.html"), null, AllIcons.ToolbarDecorator.Export);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("InspectionToolWindow.ExportPopup");
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      InspectionsBundle.message("inspection.action.export.popup.title"), group, e.getDataContext(),
      JBPopupFactory.ActionSelectionAid.MNEMONICS, true);

    InspectionResultsView.showPopup(e, popup);
  }
}
