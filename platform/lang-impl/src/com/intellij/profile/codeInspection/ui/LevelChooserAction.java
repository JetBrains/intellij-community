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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.SeverityUtil;
import com.intellij.codeInspection.ex.SeverityEditorDialog;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.profile.codeInspection.ui.table.SeverityRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public abstract class LevelChooserAction extends ComboBoxAction implements DumbAware {

  private final SeverityRegistrar mySeverityRegistrar;
  private final boolean myIncludeDoNotShow;
  private HighlightSeverity myChosen = null;

  public LevelChooserAction(final SeverityRegistrar severityRegistrar) {
    this(severityRegistrar, false);
  }

  public LevelChooserAction(final SeverityRegistrar severityRegistrar, boolean includeDoNotShow) {
    mySeverityRegistrar = severityRegistrar;
    myIncludeDoNotShow = includeDoNotShow;
  }

  @NotNull
  @Override
  public DefaultActionGroup createPopupActionGroup(final JComponent anchor) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (final HighlightSeverity severity : getSeverities(mySeverityRegistrar, myIncludeDoNotShow)) {
      final HighlightSeverityAction action = new HighlightSeverityAction(severity);
      if (myChosen == null) {
        setChosen(action.getSeverity());
      }
      group.add(action);
    }
    group.addSeparator();
    group.add(new DumbAwareAction("Edit severities...") {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final SeverityEditorDialog dlg = new SeverityEditorDialog(anchor, myChosen, mySeverityRegistrar, true);
        if (dlg.showAndGet()) {
          final HighlightInfoType type = dlg.getSelectedType();
          if (type != null) {
            final HighlightSeverity severity = type.getSeverity(null);
            setChosen(severity);
            onChosen(severity);
          }
        }
      }
    });
    return group;
  }

  public static List<HighlightSeverity> getSeverities(final SeverityRegistrar severityRegistrar) {
    return getSeverities(severityRegistrar, true);
  }

  public static List<HighlightSeverity> getSeverities(final SeverityRegistrar severityRegistrar, boolean includeDoNotShow) {
    final List<HighlightSeverity> severities = new ArrayList<>();
    for (final SeverityRegistrar.SeverityBasedTextAttributes type : SeverityUtil.getRegisteredHighlightingInfoTypes(severityRegistrar)) {
      severities.add(type.getSeverity());
    }
    if (includeDoNotShow) {
      severities.add(HighlightSeverity.INFORMATION);
    }
    severities.remove(HighlightSeverity.INFO);
    Collections.sort(severities, severityRegistrar.reversed());
    return severities;
  }

  protected abstract void onChosen(final HighlightSeverity severity);

  public void setChosen(final HighlightSeverity severity) {
    myChosen = severity;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(SingleInspectionProfilePanel.renderSeverity(severity));
    templatePresentation.setIcon(SeverityRenderer.getIcon(HighlightDisplayLevel.find(severity)));
  }

  private class HighlightSeverityAction extends DumbAwareAction {
    private final HighlightSeverity mySeverity;

    public HighlightSeverity getSeverity() {
      return mySeverity;
    }

    private HighlightSeverityAction(final HighlightSeverity severity) {
      mySeverity = severity;
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(SingleInspectionProfilePanel.renderSeverity(severity));
      presentation.setIcon(SeverityRenderer.getIcon(HighlightDisplayLevel.find(severity)));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final HighlightSeverity severity = getSeverity();
      setChosen(severity);
      onChosen(severity);
    }
  }
}
