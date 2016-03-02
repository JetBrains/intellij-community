/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.analysis;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 28-Feb-2006
 */
@State(name = "AnalysisUIOptions", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class AnalysisUIOptions implements PersistentStateComponent<AnalysisUIOptions> {
  public static AnalysisUIOptions getInstance(Project project) {
    return ServiceManager.getService(project, AnalysisUIOptions.class);
  }

  public boolean AUTOSCROLL_TO_SOURCE = false;
  public float SPLITTER_PROPORTION = 0.5f;
  public boolean GROUP_BY_SEVERITY = false;
  public boolean FILTER_RESOLVED_ITEMS = true;
  public boolean ANALYZE_TEST_SOURCES = true;
  public boolean SHOW_DIFF_WITH_PREVIOUS_RUN = false;
  public int SCOPE_TYPE = AnalysisScope.PROJECT;
  public String CUSTOM_SCOPE_NAME = "";
  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  public boolean SHOW_ONLY_DIFF = false;
  public boolean SHOW_STRUCTURE = false;

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

  @NotNull
  public AnalysisUIOptions copy() {
    final AnalysisUIOptions result = new AnalysisUIOptions();
    XmlSerializerUtil.copyBean(this, result);
    return result;
  }

  public void save(AnalysisUIOptions options) {
    XmlSerializerUtil.copyBean(options, this);
  }

  public AutoScrollToSourceHandler getAutoScrollToSourceHandler() {
    return myAutoScrollToSourceHandler;
  }

  public AnAction createGroupBySeverityAction(final InspectionResultsView view) {
    return new ToggleAction(InspectionsBundle.message("inspection.action.group.by.severity"),
                            InspectionsBundle.message("inspection.action.group.by.severity.description"),
                            AllIcons.Nodes.SortBySeverity) {


      @Override
      public boolean isSelected(AnActionEvent e) {
        return GROUP_BY_SEVERITY;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        GROUP_BY_SEVERITY = state;
        view.update();
      }
    };
  }

  public AnAction createFilterResolvedItemsAction(final InspectionResultsView view){
    return new ToggleAction(InspectionsBundle.message("inspection.filter.resolved.action.text"),
                            InspectionsBundle.message("inspection.filter.resolved.action.text"),
                            AllIcons.General.Filter) {


      @Override
      public boolean isSelected(AnActionEvent e) {
        return FILTER_RESOLVED_ITEMS;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        FILTER_RESOLVED_ITEMS = state;
        view.update();
      }
    };
  }

  public AnAction createShowOutdatedProblemsAction(final InspectionResultsView view) {
    return new ToggleAction(InspectionsBundle.message("inspection.filter.show.diff.action.text"),
                            InspectionsBundle.message("inspection.filter.show.diff.action.text"),
                            AllIcons.Actions.Diff) {


      @Override
      public boolean isSelected(AnActionEvent e) {
        return SHOW_DIFF_WITH_PREVIOUS_RUN;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        SHOW_DIFF_WITH_PREVIOUS_RUN = state;
        if (!SHOW_DIFF_WITH_PREVIOUS_RUN) {
          SHOW_ONLY_DIFF = false;
        }
        view.update();
      }
    };
  }

  public AnAction createGroupByDirectoryAction(final InspectionResultsView view) {
    return new ToggleAction("Group by directory",
                            "Group by directory",
                            AllIcons.Actions.GroupByPackage) {

      @Override
      public boolean isSelected(AnActionEvent e) {
        return SHOW_STRUCTURE;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        SHOW_STRUCTURE = state;
        view.update();
      }
    };
  }

  public AnAction createShowDiffOnlyAction(final InspectionResultsView view) {
    return new ToggleAction(InspectionsBundle.message("inspection.filter.show.diff.only.action.text"),
                            InspectionsBundle.message("inspection.filter.show.diff.only.action.text"),
                            AllIcons.Actions.ShowChangesOnly) {


      @Override
      public boolean isSelected(AnActionEvent e) {
        return SHOW_ONLY_DIFF;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        SHOW_ONLY_DIFF = state;
        view.update();
      }

      @Override
      public void update(final AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(SHOW_DIFF_WITH_PREVIOUS_RUN);
      }
    };
  }

  @Override
  public AnalysisUIOptions getState() {
    return this;
  }

  @Override
  public void loadState(AnalysisUIOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
