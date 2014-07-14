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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.SeverityUtil;
import com.intellij.codeInspection.ex.SeverityEditorDialog;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich
 */
public abstract class LevelChooserAction extends ComboBoxAction {

  private final SeverityRegistrar mySeverityRegistrar;
  private HighlightSeverity myChosen = null;

  public LevelChooserAction(final SeverityRegistrar severityRegistrar) {
    mySeverityRegistrar = severityRegistrar;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();

    final SortedSet<HighlightSeverity> severities = new TreeSet<HighlightSeverity>(mySeverityRegistrar);
    for (final SeverityRegistrar.SeverityBasedTextAttributes type : SeverityUtil.getRegisteredHighlightingInfoTypes(mySeverityRegistrar)) {
      severities.add(type.getSeverity());
    }
    severities.add(HighlightSeverity.ERROR);
    severities.add(HighlightSeverity.WARNING);
    severities.add(HighlightSeverity.WEAK_WARNING);
    severities.add(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING);
    for (final HighlightSeverity severity : severities) {
      final HighlightSeverityAction action = new HighlightSeverityAction(severity);
      if (myChosen == null) {
        setChosen(action.getSeverity());
      }
      group.add(action);
    }
    group.addSeparator();
    group.add(new AnAction("Edit severities...") {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        final SeverityEditorDialog dlg = new SeverityEditorDialog(button, myChosen, mySeverityRegistrar);
        dlg.show();
        if (dlg.isOK()) {
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

  protected abstract void onChosen(final HighlightSeverity severity);

  public void setChosen(final HighlightSeverity severity) {
    myChosen = severity;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(SingleInspectionProfilePanel.renderSeverity(severity));
    templatePresentation.setIcon(HighlightDisplayLevel.find(severity).getIcon());
  }

  private class HighlightSeverityAction extends AnAction {
    private final HighlightSeverity mySeverity;

    public HighlightSeverity getSeverity() {
      return mySeverity;
    }

    private HighlightSeverityAction(final HighlightSeverity severity) {
      mySeverity = severity;
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(SingleInspectionProfilePanel.renderSeverity(severity));
      presentation.setIcon(HighlightDisplayLevel.find(severity).getIcon());
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final HighlightSeverity severity = getSeverity();
      setChosen(severity);
      onChosen(severity);
    }
  }
}
