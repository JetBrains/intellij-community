package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ObjectUtils;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.michaelbaranov.microba.calendar.CalendarPane;
import com.michaelbaranov.microba.calendar.ui.basic.BasicCalendarPaneUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyVetoException;
import java.sql.Types;
import java.util.Date;
import java.util.EventObject;
import java.util.Locale;
import java.util.Objects;

import static com.intellij.database.extractors.FormatterCreator.getDateKey;

public final class DefaultDateEditorFactory extends DefaultTemporalEditorFactory {
  private static final Logger LOG = Logger.getInstance(DefaultDateEditorFactory.class);

  @Override
  protected @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    GridColumn c = Objects.requireNonNull(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column));
    return FormatterCreator.get(grid).create(getDateKey(c, null, FormatsCache.get(grid)));
  }

  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column) == Types.DATE ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    ValueParser parser = super.getValueParser(grid, rowIdx, columnIdx);
    return (text, document) -> {
      Object v = parser.parse(text, document);
      return v instanceof Date ? new java.sql.Date(((Date)v).getTime()) : v;
    };
  }

  @Override
  protected @NotNull FormatBasedGridCellEditor createEditorImpl(@NotNull Project project,
                                                                final @NotNull DataGrid grid,
                                                                @NotNull Formatter format,
                                                                @Nullable ReservedCellValue nullValue,
                                                                EventObject initiator,
                                                                @Nullable TextCompletionProvider provider,
                                                                @NotNull ModelIndex<GridRow> row,
                                                                @NotNull ModelIndex<GridColumn> column,
                                                                @NotNull ValueParser valueParser,
                                                                @NotNull ValueFormatter valueFormatter) {
    return new FormatBasedGridCellEditor.WithBrowseButton<CalendarPane, Date>(
      project, grid, format, nullValue, initiator, row, column, Date.class, provider, valueParser, valueFormatter, this) {
      @Override
      protected void configurePopup(@NotNull JBPopup popup, @NotNull CalendarPane component) {
        component.addActionListener(e -> {
          if (popup.isDisposed()) return;
          processDate(component, onAccept(popup), component::requestFocus);
        });
      }

      @Override
      protected Date getValue(CalendarPane component) {
        return component.getDate() == null ? new Date() : component.getDate();
      }

      @Override
      protected @NotNull CalendarPane getPopupComponent() {
        Date date = DataGridFormattersUtilCore.getDateFrom(getValue(), grid, column, FormatsCache.get(grid), FormatterCreator.get(grid));
        Date initialDate = DataGridFormattersUtilCore.getBoundedValue(date, column, grid);
        return new MyCalendarPane(initialDate, getNullValue() != null);
      }

      @Override
      protected @NotNull JComponent getPreferredFocusedComponent(@NotNull CalendarPane popupComponent) {
        if (popupComponent.getComponentCount() != 5) {
          LOG.warn("Unexpected number of components on calendar pane. Expected 5, actual: " + popupComponent.getComponentCount() + ". Initial focus won't be set to month combobox.");
          return popupComponent;
        }
        JPanel calendarPanel = ObjectUtils.tryCast(popupComponent.getComponent(0), JPanel.class); // ModernCalendarPanel

        if (calendarPanel == null) {
          LOG.warn("ModernCalendarPanel is not found on calendar pane. Initial focus won't be set to month combobox.");
          return popupComponent;
        }
        return Objects.requireNonNull(ObjectUtils.tryCast(calendarPanel.getComponent(0), JComponent.class));
      }
    };
  }

  private static class MyCalendarPane extends CalendarPane {
    private boolean mySettingDate;

    MyCalendarPane(Date initialDate, boolean showNoneButton) {
      super(initialDate, 0, Locale.getDefault(), DataGridFormattersUtilCore.getDefaultTimeZone());
      setFocusLostBehavior(JFormattedTextField.PERSIST);
      setShowNoneButton(showNoneButton);
      setShowNumberOfWeek(true);
      setShowTodayButton(true);
      setStripTime(false);
      setFocusCycleRoot(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
      setPaintBorderForFocusedButtons();
    }

    private void setPaintBorderForFocusedButtons() {
      JPanel auxPanel = ObjectUtils.tryCast(getComponent(4), JPanel.class); // AuxPanel
      if (auxPanel == null) {
        LOG.warn("AuxPanel is not found on calendar pane. Focused buttons won't be highlighted");
        return;
      }

      JButton todayButton = ObjectUtils.tryCast(auxPanel.getComponent(0), JButton.class);
      JButton noneButton = ObjectUtils.tryCast(auxPanel.getComponent(1), JButton.class);
      if (todayButton == null || noneButton == null) return;
      setPaintBorderForFocusedButtons(todayButton);
      setPaintBorderForFocusedButtons(noneButton);
    }

    private static void setPaintBorderForFocusedButtons(@NotNull JButton button) {
      button.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          button.setBorderPainted(true);
        }

        @Override
        public void focusLost(FocusEvent e) {
          button.setBorderPainted(false);
        }
      });
    }

    @Override
    public void updateUI() {
      setUI(new BasicCalendarPaneUI() {
        @Override
        protected void createNestedComponents() {
          super.createNestedComponents();
          JComponent gridComponent = gridPanel;
          ActionMap actionMap = gridComponent.getActionMap();
          actionMap.put("##microba.commit##", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              try {
                commit();
              }
              catch (PropertyVetoException ignore) {
              }
            }
          });
        }
      });
      invalidate();
    }

    @Override
    public void setDate(Date date) throws PropertyVetoException {
      if (mySettingDate) {
        super.setDate(date);
      }
      else {
        doSetDate(date);
      }
    }

    private void doSetDate(Date date) throws PropertyVetoException {
      mySettingDate = true;
      try {
        boolean dateChanged = !Comparing.equal(date, getDate());
        super.setDate(date);
        if (!dateChanged) {
          // microba uses property change events and they don't get fired if the value isn't changed
          // so we fire an action event which would have been caused by property change.
          fireActionEvent();
        }
      }
      finally {
        mySettingDate = false;
      }
    }
  }
}
