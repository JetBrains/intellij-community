// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getView;

public class InspectionResultsExportAction extends AnAction implements DumbAware {

  public InspectionResultsExportAction() {
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

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final InspectionResultsView view = getView(e);
    presentation.setEnabledAndVisible(view != null && ActionPlaces.CODE_INSPECTION.equals(e.getPlace()));
  }
}