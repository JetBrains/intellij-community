// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
@Service(Service.Level.PROJECT)
@State(name = "AnalysisUIOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class AnalysisUIOptions implements PersistentStateComponent<AnalysisUIOptions> {
  public static AnalysisUIOptions getInstance(Project project) {
    return project.getService(AnalysisUIOptions.class);
  }

  public boolean AUTOSCROLL_TO_SOURCE = false;
  public float SPLITTER_PROPORTION = 0.5f;
  public volatile boolean GROUP_BY_SEVERITY = false;
  public volatile boolean FILTER_RESOLVED_ITEMS = true;
  public boolean ANALYZE_TEST_SOURCES = true;
  public boolean ANALYZE_INJECTED_CODE = true;
  @AnalysisScope.Type
  public int SCOPE_TYPE = AnalysisScope.PROJECT;
  public String CUSTOM_SCOPE_NAME = "";
  @Transient
  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  public volatile boolean SHOW_STRUCTURE = false;
  public @NlsSafe String FILE_MASK;

  public boolean ANALYSIS_IN_BACKGROUND = true;

  public AnalysisUIOptions() {
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return AUTOSCROLL_TO_SOURCE;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        AUTOSCROLL_TO_SOURCE = state;
      }
    };

  }

  public AutoScrollToSourceHandler getAutoScrollToSourceHandler() {
    return myAutoScrollToSourceHandler;
  }

  public AnAction createGroupBySeverityAction(final InspectionResultsView view) {
    return new InspectionResultsViewToggleAction(
      view,
      InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.group.by.severity.new" : "inspection.action.group.by.severity"),
      InspectionsBundle.message("inspection.action.group.by.severity.description"),
      AllIcons.Nodes.SortBySeverity) {


      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return GROUP_BY_SEVERITY;
      }

      @Override
      protected void setSelected(boolean state) {
        GROUP_BY_SEVERITY = state;
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
  }

  public AnAction createFilterResolvedItemsAction(final InspectionResultsView view){
    return new InspectionResultsViewToggleAction(view,
                                                 InspectionsBundle.message("inspection.filter.resolved.action.text"),
                                                 InspectionsBundle.message("inspection.filter.resolved.action.text"),
                                                 AllIcons.General.Filter) {


      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return FILTER_RESOLVED_ITEMS;
      }

      @Override
      public void setSelected(boolean state) {
        FILTER_RESOLVED_ITEMS = state;
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
  }

  public AnAction createGroupByDirectoryAction(final InspectionResultsView view) {
    String message = InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.group.by.directory.new" :
                                               "inspection.action.group.by.directory");
    return new InspectionResultsViewToggleAction(view, message, message, AllIcons.Actions.GroupByPackage) {

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return SHOW_STRUCTURE;
      }

      @Override
      public void setSelected(boolean state) {
        SHOW_STRUCTURE = state;
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
  }

  @Override
  public AnalysisUIOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AnalysisUIOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  private abstract static class InspectionResultsViewToggleAction extends ToggleAction implements DumbAware {
    private final @NotNull InspectionResultsView myView;

    InspectionResultsViewToggleAction(@NotNull InspectionResultsView view,
                                      @NotNull @NlsActions.ActionText String text,
                                      @NotNull @NlsActions.ActionDescription String description,
                                      @NotNull Icon icon) {
      super(text, description, icon);
      myView = view;
    }

    @Override
    public final void setSelected(@NotNull AnActionEvent e, boolean state) {
      setSelected(state);
      myView.update();
    }

    protected abstract void setSelected(boolean state);
  }
}
