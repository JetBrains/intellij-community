package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class InspectionsOptionsToolbarAction extends AnAction {
  private final InspectionResultsView myView;

  public InspectionsOptionsToolbarAction(final InspectionResultsView view) {
    super(getToolOptions(null), getToolOptions(null), AllIcons.General.InspectionsOff);
    myView = view;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(getSelectedToolWrapper().getDisplayName(),
                              (ActionGroup)ActionManager.getInstance().getAction("InspectionsOptions"),
                              e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    InspectionResultsView.showPopup(e, popup);
  }

  @Nullable
  private InspectionToolWrapper getSelectedToolWrapper() {
    return myView.getTree().getSelectedToolWrapper();
  }

  @Override
  public void update(AnActionEvent e) {
    if (!myView.isSingleToolInSelection()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    InspectionToolWrapper toolWrapper = getSelectedToolWrapper();
    assert toolWrapper != null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
    if (key == null) {
      e.getPresentation().setEnabled(false);
    }
    e.getPresentation().setEnabled(true);
    final String text = getToolOptions(toolWrapper);
    e.getPresentation().setText(text);
    e.getPresentation().setDescription(text);
  }

  @NotNull
  private static String getToolOptions(@Nullable final InspectionToolWrapper toolWrapper) {
    return InspectionsBundle.message("inspections.view.options.title", toolWrapper != null ? toolWrapper.getDisplayName() : "");
  }
}
