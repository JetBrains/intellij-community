// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.SeverityEditorDialog;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.EventObject;

public class SeverityRenderer extends ComboBoxTableRenderer<HighlightSeverity> {
  private static final Icon DEFAULT_DISABLED_ICON = HighlightDisplayLevel.createIconByMask(UIUtil.getLabelDisabledForeground());

  static final HighlightSeverity EDIT_SEVERITIES = new HighlightSeverity("-", -1);

  private final @NotNull Runnable myOnClose;
  private final ScopesAndSeveritiesTable myTable;
  private final @NotNull Project myProject;

  public SeverityRenderer(@NotNull InspectionProfileImpl inspectionProfile,
                          @NotNull Project project,
                          @NotNull Runnable onClose,
                          @NotNull ScopesAndSeveritiesTable table) {
    super(getSeverities(inspectionProfile));
    myOnClose = onClose;
    myTable = table;
    myProject = project;
  }

  private static HighlightSeverity[] getSeverities(InspectionProfileImpl profile) {
    List<HighlightSeverity> list = new ArrayList<>(LevelChooserAction.getSeverities(profile.getProfileManager().getSeverityRegistrar()));
    list.add(EDIT_SEVERITIES);
    return list.toArray(new HighlightSeverity[0]);
  }

  public static Icon getIcon(@NotNull HighlightDisplayLevel level) {
    Icon icon = level.getIcon();
    return icon instanceof HighlightDisplayLevel.ColoredIcon
                 ? new ColorIcon(icon.getIconWidth(), ((HighlightDisplayLevel.ColoredIcon)icon).getColor())
                 : icon;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    component.setForeground(RenderingUtil.getForeground(table, isSelected));
    component.setBackground(RenderingUtil.getBackground(table, isSelected));
    component.setEnabled(((ScopesAndSeveritiesTable)table).isRowEnabled(row));
    return component;
  }

  @Override
  protected void customizeComponent(HighlightSeverity value, JTable table, boolean isSelected) {
    super.customizeComponent(value, table, isSelected);
    HighlightDisplayLevel hdl = HighlightDisplayLevel.find(value == null ? HighlightSeverity.INFORMATION : value);
    setDisabledIcon(hdl != null ? IconLoader.getDisabledIcon(hdl.getIcon()) : DEFAULT_DISABLED_ICON);
  }

  @Override
  protected String getTextFor(@NotNull HighlightSeverity value) {
    return value == EDIT_SEVERITIES
           ? InspectionsBundle.message("inspection.edit.severities.item")
           : SingleInspectionProfilePanel.renderSeverity(value);
  }

  @Override
  protected Icon getIconFor(@NotNull HighlightSeverity value) {
    return value == EDIT_SEVERITIES
           ? EmptyIcon.create(HighlightDisplayLevel.getEmptyIconDim())
           : HighlightDisplayLevel.find(value).getIcon();
  }

  @Override
  public boolean isCellEditable(EventObject event) {
    return !(event instanceof MouseEvent) || ((MouseEvent)event).getClickCount() >= 1;
  }

  @Override
  protected ListSeparator getSeparatorAbove(HighlightSeverity value) {
    return value == EDIT_SEVERITIES ? new ListSeparator() : null;
  }

  @Override
  public void onClosed(@NotNull LightweightWindowEvent event) {
    super.onClosed(event);
    myOnClose.run();
    if (getCellEditorValue() == EDIT_SEVERITIES) {
      ApplicationManager.getApplication().invokeLater( () -> SeverityEditorDialog.show(
        myProject, null, SeverityRegistrar.getSeverityRegistrar(myProject), true, severity -> myTable.setSelectedSeverity(severity)));
    }
  }
}
