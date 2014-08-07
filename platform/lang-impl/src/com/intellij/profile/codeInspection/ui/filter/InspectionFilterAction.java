/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.filter;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;

/**
 * @author Dmitry Batkovich
 */
public class InspectionFilterAction extends DefaultActionGroup {

  private final SeverityRegistrar mySeverityRegistrar;
  private final InspectionsFilter myInspectionsFilter;

  public InspectionFilterAction(final InspectionProfileImpl profile, final InspectionsFilter inspectionsFilter) {
    super("Filter Inspections", true);
    myInspectionsFilter = inspectionsFilter;
    mySeverityRegistrar = ((SeverityProvider)profile.getProfileManager()).getOwnSeverityRegistrar();
    getTemplatePresentation().setIcon(AllIcons.General.Filter);
    tune();
  }

  private void tune() {
    addAction(new ShowEnabledOrDisabledInspectionsAction(null));
    addAction(new ShowEnabledOrDisabledInspectionsAction(true));
    addAction(new ShowEnabledOrDisabledInspectionsAction(false));
    addSeparator();

    final SortedSet<HighlightSeverity> severities = LevelChooserAction.getSeverities(mySeverityRegistrar);
    for (final HighlightSeverity severity : severities) {
      add(new ShowWithSpecifiedSeverityInspectionsAction(severity));
    }
    addSeparator();

    add(new ShowAvailableOnlyOnAnalyzeInspectionsAction());
    add(new ShowOnlyCleanupInspectionsAction());
  }

  private class ShowOnlyCleanupInspectionsAction extends CheckboxAction {
    public ShowOnlyCleanupInspectionsAction() {
      super("Show Only Cleanup Inspections");
    }

    @Override
    public boolean isSelected(final AnActionEvent e) {
      return myInspectionsFilter.isShowOnlyCleanupInspections();
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
      myInspectionsFilter.setShowOnlyCleanupInspections(state);
    }
  }

  private class ShowAvailableOnlyOnAnalyzeInspectionsAction extends CheckboxAction {

    public ShowAvailableOnlyOnAnalyzeInspectionsAction() {
      super("Show Only \"Available only for Analyze | Inspect Code\"");
    }

    @Override
    public boolean isSelected(final AnActionEvent e) {
      return myInspectionsFilter.isAvailableOnlyForAnalyze();
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
      myInspectionsFilter.setAvailableOnlyForAnalyze(state);
    }
  }

  private class ShowWithSpecifiedSeverityInspectionsAction extends CheckboxAction {

    private final HighlightSeverity mySeverity;

    private ShowWithSpecifiedSeverityInspectionsAction(final HighlightSeverity severity) {
      super(SingleInspectionProfilePanel.renderSeverity(severity),
            null,
            HighlightDisplayLevel.find(severity).getIcon());
      mySeverity = severity;
    }


    @Override
    public boolean isSelected(final AnActionEvent e) {
      return myInspectionsFilter.containsSeverity(mySeverity);
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
      if (state) {
        myInspectionsFilter.add(mySeverity);
      } else {
        myInspectionsFilter.remove(mySeverity);
      }
    }
  }

  private class ShowEnabledOrDisabledInspectionsAction extends CheckboxAction {

    private final Boolean myShowEnabledActions;

    public ShowEnabledOrDisabledInspectionsAction(@Nullable final Boolean showEnabledActions) {
      super(showEnabledActions == null ? "All Inspections" : (showEnabledActions ? "Enabled" : "Disabled"));
      myShowEnabledActions = showEnabledActions;
    }


    @Override
    public boolean isSelected(final AnActionEvent e) {
      return myInspectionsFilter.getSuitableInspectionsStates() == myShowEnabledActions;
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
      myInspectionsFilter.setSuitableInspectionsStates(myShowEnabledActions);
    }
  }
}