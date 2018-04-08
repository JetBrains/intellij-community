// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.List;

public class SeverityRenderer extends ComboBoxTableRenderer<SeverityState> {
  private final Runnable myOnClose;
  private final Icon myDisabledIcon;

  public SeverityRenderer(final SeverityState[] values, @Nullable final Runnable onClose) {
    super(values);
    myOnClose = onClose;
    myDisabledIcon = HighlightDisplayLevel.createIconByMask(UIUtil.getLabelDisabledForeground());
  }

  public static SeverityRenderer create(final InspectionProfileImpl inspectionProfile, @Nullable final Runnable onClose) {
    return new SeverityRenderer(LevelChooserAction.getSeverities(inspectionProfile.getProfileManager().getOwnSeverityRegistrar())
                                                  .stream()
                                                  .map(s -> new SeverityState(s, false))
                                                  .toArray(SeverityState[]::new), onClose);
  }

  public static Icon getIcon(@NotNull HighlightDisplayLevel level) {
    Icon icon = level.getIcon();
    return icon instanceof HighlightDisplayLevel.ColoredIcon
                 ? new ColorIcon(icon.getIconWidth(), ((HighlightDisplayLevel.ColoredIcon)icon).getColor())
                 : icon;
  }

  @Override
  protected void customizeComponent(SeverityState value, JTable table, boolean isSelected) {
    super.customizeComponent(value, table, isSelected);
    setEnabled(!value.isDisabled());
    setDisabledIcon(myDisabledIcon);
  }

  @Override
  protected String getTextFor(@NotNull final SeverityState value) {
    return SingleInspectionProfilePanel.renderSeverity(value.getSeverity());
  }

  @Override
  protected Icon getIconFor(@NotNull final SeverityState value) {
    return getIcon(HighlightDisplayLevel.find(value.getSeverity()));
  }

  @Override
  public boolean isCellEditable(final EventObject event) {
    return !(event instanceof MouseEvent) || ((MouseEvent)event).getClickCount() >= 1;
  }

  @Override
  public void onClosed(LightweightWindowEvent event) {
    super.onClosed(event);
    if (myOnClose != null) {
      myOnClose.run();
    }
  }
}
