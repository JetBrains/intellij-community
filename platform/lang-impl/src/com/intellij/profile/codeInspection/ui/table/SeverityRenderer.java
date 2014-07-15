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
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.SortedSet;

/**
 * @author Dmitry Batkovich
 */
public class SeverityRenderer extends ComboBoxTableRenderer<HighlightSeverity> {
  public SeverityRenderer(final HighlightSeverity[] values) {
    super(values);
  }

  public static SeverityRenderer create(final InspectionProfileImpl inspectionProfile) {
    final SortedSet<HighlightSeverity> severities =
      LevelChooserAction.getSeverities(((SeverityProvider)inspectionProfile.getProfileManager()).getOwnSeverityRegistrar());
    return new SeverityRenderer(severities.toArray(new HighlightSeverity[severities.size()]));
  }


  @Override
  protected String getTextFor(@NotNull final HighlightSeverity value) {
    return SingleInspectionProfilePanel.renderSeverity(value);
  }

  @Override
  protected Icon getIconFor(@NotNull final HighlightSeverity value) {
    return HighlightDisplayLevel.find(value).getIcon();
  }

  @Override
  public boolean isCellEditable(final EventObject event) {
    if (event instanceof MouseEvent) {
      return ((MouseEvent)event).getClickCount() >= 1;
    }
    return true;
  }
}
