// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.actions.export.ExportActionBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

public class ExportAction extends AnAction implements DumbAware {
  @NonNls private static final String CODE_INSPECTION_EXPORT_PREFIX = "CodeInspectionExport";
  private final InspectionResultsView myView;
  private final Map<String, ExportActionBase> myExportActions;

  public ExportAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.export.html"), null, AllIcons.ToolbarDecorator.Export);
    myView = view;
    myExportActions = getExportActions();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<String>(InspectionsBundle.message("inspection.action.export.popup.title"), newArrayList(myExportActions.keySet())) {
        @Override
        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          return doFinalStep(() -> export(selectedValue));
        }
      });
    InspectionResultsView.showPopup(e, popup);
  }

  protected Map<String, ExportActionBase> getExportActions() {
    Map<String, ExportActionBase> exportActions = new TreeMap<>();
    String[] ids = ActionManager.getInstance().getActionIds(CODE_INSPECTION_EXPORT_PREFIX);
    for (String actionId: ids) {
      ExportActionBase action = (ExportActionBase)ActionManager.getInstance().getAction(actionId);
      if (action.isVisible(myView)) {
        exportActions.put(action.getTemplatePresentation().getText(), action);
      }
    }
    return exportActions;
  }

  private void export(final String selectedValue) {
    ExportActionBase exportAction = myExportActions.get(selectedValue);
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myView.getProject(), exportAction.isExportToHTML());
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myView.getProject());
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "inspections";
    }
    exportToHTMLDialog.reset();
    if (!exportToHTMLDialog.showAndGet()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;

    exportAction.actionPerformed(myView, outputDirectoryName, exportToHTMLSettings);
  }

}
