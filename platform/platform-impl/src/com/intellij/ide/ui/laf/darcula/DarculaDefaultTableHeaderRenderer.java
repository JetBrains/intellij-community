package com.intellij.ide.ui.laf.darcula;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaDefaultTableHeaderRenderer extends DefaultTableCellRenderer implements UIResource {
  public DarculaDefaultTableHeaderRenderer() {
    setHorizontalAlignment(SwingConstants.CENTER);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus, int row, int column) {
    //if (table == null) {
    //  setBorder(DefaultTableCellRenderer.noFocusBorder);
    //  setValue(value);
    //  setOpaque(false);
    //  return this;
    //}
    //
    //if (table.getTableHeader() == null) {
    //  return super.getTableCellRendererComponent(table, value,
    //                                             isSelected, hasFocus, row, column);
    //}
    //
    //JTableHeader tableHeader = table.getTableHeader();
    //TableHeaderUI tableHeaderUI = tableHeader.getUI();
    //if (UIUtil.isUnderDarcula() && (tableHeaderUI instanceof DarculaTableHeaderUI)) {
    //  DarculaTableHeaderUI ui = (DarculaTableHeaderUI) tableHeaderUI;

    //  StateTransitionTracker.ModelStateInfo modelStateInfo = ui
    //    .getModelStateInfo(column);
    //  ComponentState currState = ui.getColumnState(column);
    //
    //  if (modelStateInfo != null) {
    //    Map<ComponentState, StateContributionInfo> activeStates = modelStateInfo
    //      .getStateContributionMap();
    //    SubstanceColorScheme colorScheme = getColorSchemeForState(
    //      tableHeader, currState);
    //    if (currState.isDisabled() || (activeStates == null)
    //        || (activeStates.size() == 1)) {
    //      super.setForeground(new ColorUIResource(colorScheme
    //                                                .getForegroundColor()));
    //    } else {
    //      float aggrRed = 0;
    //      float aggrGreen = 0;
    //      float aggrBlue = 0;
    //
    //      for (Map.Entry<ComponentState, StateTransitionTracker.StateContributionInfo> activeEntry : modelStateInfo
    //        .getStateContributionMap().entrySet()) {
    //        ComponentState activeState = activeEntry.getKey();
    //        SubstanceColorScheme scheme = getColorSchemeForState(
    //          tableHeader, activeState);
    //        Color schemeFg = scheme.getForegroundColor();
    //        float contribution = activeEntry.getValue()
    //          .getContribution();
    //        aggrRed += schemeFg.getRed() * contribution;
    //        aggrGreen += schemeFg.getGreen() * contribution;
    //        aggrBlue += schemeFg.getBlue() * contribution;
    //      }
    //      super.setForeground(new ColorUIResource(new Color(
    //        (int) aggrRed, (int) aggrGreen, (int) aggrBlue)));
    //    }
    //  } else {
    //    SubstanceColorScheme scheme = getColorSchemeForState(
    //      tableHeader, currState);
    //    super.setForeground(new ColorUIResource(scheme
    //                                              .getForegroundColor()));
    //  }
    //} else {
    //  super.setForeground(table.getForeground());
    //}
    //
    //this.setBackground(tableHeader.getBackground());
    //
    //// fix for issue 319 - using font from the table header
    //if (tableHeader.getFont() != null) {
    //  setFont(tableHeader.getFont());
    //} else {
    //  setFont(table.getFont());
    //}
    //
    //TableUI tableUI = table.getUI();
    //if (SubstanceLookAndFeel.isCurrentLookAndFeel()
    //    && (tableUI instanceof SubstanceTableUI)) {
    //  this.setBorder(new EmptyBorder(((SubstanceTableUI) tableUI)
    //                                   .getCellRendererInsets()));
    //}
    //
    //this.setValue(value);
    //this.setOpaque(false);
    //
    //this.setEnabled(tableHeader.isEnabled() && table.isEnabled());
    //
    //// fix for defect 242 - not showing sort icon
    //if (SubstanceLookAndFeel.isCurrentLookAndFeel()) {
    //  this.setIcon(null);
    //  RowSorter<? extends TableModel> rowSorter = table.getRowSorter();
    //  if (rowSorter != null) {
    //    setHorizontalTextPosition(JLabel.LEADING);
    //    java.util.List<? extends RowSorter.SortKey> sortKeys = rowSorter
    //      .getSortKeys();
    //    Icon sortIcon = null;
    //    SubstanceColorScheme scheme = null;
    //    if (tableHeaderUI instanceof SubstanceTableHeaderUI) {
    //      SubstanceTableHeaderUI ui = (SubstanceTableHeaderUI) tableHeaderUI;
    //      ComponentState state = ui.getColumnState(column);
    //      ColorSchemeAssociationKind colorSchemeAssociationKind = (state == ComponentState.ENABLED) ? ColorSchemeAssociationKind.MARK
    //                                                                                                : ColorSchemeAssociationKind.HIGHLIGHT_MARK;
    //      scheme = SubstanceColorSchemeUtilities.getColorScheme(
    //        tableHeader, colorSchemeAssociationKind, state);
    //    } else {
    //      scheme = SubstanceColorSchemeUtilities.getColorScheme(
    //        tableHeader, ComponentState.ENABLED);
    //    }
    //
    //    if (sortKeys.size() > 0
    //        && sortKeys.get(0).getColumn() == table
    //      .convertColumnIndexToModel(column)) {
    //      switch (sortKeys.get(0).getSortOrder()) {
    //        case ASCENDING:
    //          sortIcon = SubstanceImageCreator.getArrowIcon(
    //            SubstanceSizeUtils
    //              .getComponentFontSize(tableHeader),
    //            SwingConstants.NORTH, scheme);
    //          break;
    //        case DESCENDING:
    //          sortIcon = SubstanceImageCreator.getArrowIcon(
    //            SubstanceSizeUtils
    //              .getComponentFontSize(tableHeader),
    //            SwingConstants.SOUTH, scheme);
    //          break;
    //        case UNSORTED:
    //          sortIcon = null;
    //      }
    //      this.setIcon(sortIcon);
    //    }
    //  }
    //}

    return this;
  }

}
