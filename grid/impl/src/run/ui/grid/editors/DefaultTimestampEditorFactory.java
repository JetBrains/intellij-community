package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.ui.CalendarView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.EventObject;
import java.util.Objects;

import static com.intellij.database.extractors.FormatterCreator.*;

public class DefaultTimestampEditorFactory extends DefaultTemporalEditorFactory {
  private final CalendarView.Mode myCalendarMode;

  public DefaultTimestampEditorFactory(@NotNull CalendarView.Mode mode) {
    myCalendarMode = mode;
  }

  protected final @NotNull CalendarView.Mode getCalendarMode() {
    return myCalendarMode;
  }

  protected final int getExpectedJdbcType() {
    return switch (myCalendarMode) {
      case DATE -> Types.DATE;
      case TIME -> Types.TIME;
      case DATETIME -> Types.TIMESTAMP;
    };
  }

  protected final @NotNull FormatterCreator.FormatterKey<? extends Formatter> getFormatterKey(@NotNull DataGrid grid, @Nullable GridColumn c) {
    FormatsCache formatsCache = FormatsCache.get(grid);
    return switch (myCalendarMode) {
      case DATE -> getDateKey(c, null, formatsCache);
      case TIME -> getTimeKey(c, null, formatsCache);
      case DATETIME -> getTimestampKey(c, null, formatsCache);
    };
  }

  @Override
  protected @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    GridColumn c = Objects.requireNonNull(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column));
    return FormatterCreator.get(grid).create(getFormatterKey(grid, c));
  }

  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column) == getExpectedJdbcType()
           ? SUITABILITY_MIN
           : SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    ValueParser parser = super.getValueParser(grid, rowIdx, columnIdx);
    return (text, document) -> {
      Object v = parser.parse(text, document);
      if (!(v instanceof Date date)) return v;
      return switch (myCalendarMode) {
        case DATE -> date instanceof java.sql.Date ? date : new java.sql.Date(date.getTime());
        case TIME -> date instanceof Time ? date : new Time(date.getTime());
        case DATETIME -> date instanceof Timestamp ? date : new Timestamp(date.getTime());
      };
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
    return new FormatBasedGridCellEditor.WithBrowseButton<CalendarView, Date>(
      project, grid, format, nullValue, initiator, row, column, Date.class, provider, valueParser, valueFormatter, this) {

      @Override
      protected @NotNull CalendarView getPopupComponent() {
        CalendarView calendarView = new CalendarView(myCalendarMode);
        calendarView.setFocusCycleRoot(true);
        calendarView.getCalendar().setTimeZone(DataGridFormattersUtilCore.getDefaultTimeZone());
        Date initial = DataGridFormattersUtilCore.getBoundedValue(
          DataGridFormattersUtilCore.getDateFrom(getValue(), grid, column, FormatsCache.get(grid), FormatterCreator.get(grid)), column, grid);
        calendarView.setDate(initial);
        return calendarView;
      }

      @Override
      protected @NotNull JComponent getPreferredFocusedComponent(@NotNull CalendarView popupComponent) {
        return popupComponent.getDaysCombo();
      }

      @Override
      protected void configurePopup(@NotNull JBPopup popup, @NotNull CalendarView component) {
        component.registerEnterHandler(() -> processDate(component, onAccept(popup),
                                                         () -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                                                           IdeFocusManager.getGlobalInstance().requestFocus(component.getDaysCombo(), true)
                                                         )));
      }

      @Override
      protected Date getValue(CalendarView component) {
        long unixTime = component.getDate().getTime();
        return switch (myCalendarMode) {
          case DATE -> new java.sql.Date(unixTime);
          case TIME -> new Time(unixTime);
          case DATETIME -> new Timestamp(unixTime);
        };
      }
    };
  }
}
