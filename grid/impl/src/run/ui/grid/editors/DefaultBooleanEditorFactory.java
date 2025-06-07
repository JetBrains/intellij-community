package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.grid.renderers.DefaultBooleanRendererFactory;
import com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory;
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer;
import com.intellij.database.settings.DataGridAppearanceSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.text.ParsePosition;
import java.util.EnumSet;
import java.util.EventObject;
import java.util.Objects;

public class DefaultBooleanEditorFactory implements GridCellEditorFactory {

  protected static final Formatter myParser = new FormatterImpl() {
    @Override
    protected @Nls @NotNull String getErrorMessage() {
      return DataGridBundle.message("expected.boolean.value");
    }

    @Override
    public Object parse(@NotNull String value, ParsePosition position) {
      String text = StringUtil.trim(value);
      if (StringUtil.equalsIgnoreCase(text, "true") || StringUtil.equalsIgnoreCase(text, "1")) {
        position.setIndex(value.length());
        return Boolean.TRUE;
      }
      if (StringUtil.equalsIgnoreCase(text, "false") || StringUtil.equalsIgnoreCase(text, "0")) {
        position.setIndex(value.length());
        return Boolean.FALSE;
      }
      position.setErrorIndex(0);
      return null;
    }

    @Override
    public String format(Object value) {
      throw new UnsupportedOperationException();
    }
  };


  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column);
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    GridColumn c = Objects.requireNonNull(model.getColumn(column));
    return ObjectFormatterUtil.isBooleanColumn(c, type) ?
           SUITABILITY_MIN + 1 :
           SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull IsEditableChecker getIsEditableChecker() {
    return (value, grid, column) -> true;
  }

  @Override
  public @NotNull GridCellEditor createEditor(@NotNull DataGrid grid,
                                              @NotNull ModelIndex<GridRow> row,
                                              @NotNull ModelIndex<GridColumn> column,
                                              @Nullable Object object,
                                              EventObject initiator) {
    EnumSet<ReservedCellValue> opts = GridCellEditorHelper.get(grid).getSpecialValues(grid, column);
    KeyEvent keyEvent = ObjectUtils.tryCast(initiator, KeyEvent.class);
    Object typedValue = null;
    if (keyEvent != null) {
      int code = keyEvent.getKeyCode();
      typedValue = KeyEvent.VK_T == code || KeyEvent.VK_1 == code ? true :
                   KeyEvent.VK_F == code || KeyEvent.VK_0 == code ? false :
                   KeyEvent.VK_N == code && opts.contains(ReservedCellValue.NULL) ? ReservedCellValue.NULL :
                   KeyEvent.VK_D == code && opts.contains(ReservedCellValue.DEFAULT) ? ReservedCellValue.DEFAULT :
                   KeyEvent.VK_C == code && opts.contains(ReservedCellValue.COMPUTED) ? ReservedCellValue.COMPUTED :
                   KeyEvent.VK_G == code && opts.contains(ReservedCellValue.GENERATED) ? ReservedCellValue.GENERATED :
                   KeyEvent.VK_SPACE == code ? !Boolean.TRUE.equals(object) :
                   null;
    }
    return grid.getAppearance().getBooleanMode() == DataGridAppearanceSettings.BooleanMode.TEXT
           ? new TextBooleanCellEditor(grid, row, column, object == null ? ReservedCellValue.NULL : object, opts, typedValue)
           : new CheckboxBooleanCellEditor(grid, row, column, object == null ? ReservedCellValue.NULL : object, typedValue);
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    GridCellEditorHelper editorHelper = GridCellEditorHelper.get(grid);
    return new ValueParserWrapper(myParser, editorHelper.isNullable(grid, columnIdx),
                                    editorHelper.getDefaultNullValue(grid, columnIdx),
                                    (text, e) -> editorHelper.createUnparsedValue(text, e, grid, rowIdx, columnIdx));
  }

  @Override
  public @NotNull GridCellEditorFactory.ValueFormatter getValueFormatter(@NotNull DataGrid grid,
                                                                         @NotNull ModelIndex<GridRow> rowIdx,
                                                                         @NotNull ModelIndex<GridColumn> columnIdx,
                                                                         @Nullable Object value) {
    return new DefaultValueToText(grid, columnIdx, value);
  }

  private abstract static class AbstractBooleanCellEditor extends GridCellEditor.Adapter {
    final DataGrid myGrid;
    final Object myInitialValue;
    private final boolean shouldMoveFocus;
    final JComponent myComponent;
    Object myValue;

    AbstractBooleanCellEditor(@NotNull DataGrid grid,
                              @NotNull ModelIndex<GridRow> row,
                              @NotNull ModelIndex<GridColumn> column,
                              @NotNull Object value,
                              @Nullable Object typedValue) {
      myGrid = grid;
      myInitialValue = value;
      shouldMoveFocus = typedValue == null;
      //noinspection AbstractMethodCallInConstructor
      myComponent = createComponent(row, column, value);
      if (typedValue != null) {
        myComponent.addFocusListener(new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            setValue(typedValue);
            editingEnded();
          }

          @Override
          public void focusLost(FocusEvent e) { }
        });
      }
    }

    @Override
    public @NotNull String getText() {
      return myInitialValue.toString();
    }

    abstract JComponent createComponent(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @NotNull Object value);

    void setValue(@NotNull Object value) {
      myValue = value;
      fireEditing(value);
      myGrid.getResultView().getComponent().requestFocus();
    }

    void editingEnded() {
      if (myValue == null) {
        myGrid.cancelEditing();
      }
      else {
        if (!myGrid.stopEditing()) {
          myGrid.cancelEditing();
        }
      }
    }

    @Override
    public @Nullable Object getValue() {
      return myValue != null ? myValue : myInitialValue;
    }

    @Override
    public boolean isColumnSpanAllowed() {
      return false;
    }

    @Override
    public boolean shouldMoveFocus() {
      return shouldMoveFocus;
    }
  }

  private static final class TextBooleanCellEditor extends AbstractBooleanCellEditor {
    final EnumSet<ReservedCellValue> myAllowedValues;

    private TextBooleanCellEditor(@NotNull DataGrid grid,
                                  @NotNull ModelIndex<GridRow> row,
                                  @NotNull ModelIndex<GridColumn> column,
                                  @NotNull Object value,
                                  @NotNull EnumSet<ReservedCellValue> allowedValues,
                                  @Nullable Object typedValue) {
      super(grid, row, column, value, typedValue);
      myAllowedValues = allowedValues;
      if (typedValue == null) {
        Ref<Boolean> popupShown = new Ref<>(false);
        myComponent.addFocusListener(new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            if (!popupShown.get()) {
              popupShown.set(true);
              showPopup();
            }
            else {
              editingEnded();
            }
          }

          @Override
          public void focusLost(FocusEvent e) { }
        });
      }
    }

    private void showPopup() {
      Object[] options = ArrayUtil.mergeArrays(new Object[]{Boolean.TRUE, Boolean.FALSE}, ArrayUtil.toObjectArray(myAllowedValues));
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, new ChooseBooleanActionGroup(options, this),
                                                                             DataManager.getInstance().getDataContext(getComponent()),
                                                                             JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      popup.showUnderneathOf(myComponent);
    }

    @Override
    protected @NotNull JComponent createComponent(@NotNull ModelIndex<GridRow> row,
                                                  @NotNull ModelIndex<GridColumn> column,
                                                  @NotNull Object value) {
      GridCellRenderer myRenderer = value instanceof UnparsedValue
                                    ? new DefaultTextRendererFactory.TextRenderer(myGrid)
                                    : new DefaultBooleanRendererFactory.TextBooleanRenderer(myGrid);
      Disposer.register(this, myRenderer);
      Pair<Integer, Integer> viewRowAndColumn = myGrid.getRawIndexConverter().rowAndColumn2View().fun(row.asInteger(), column.asInteger());
      ViewIndex<GridRow> viewRow = ViewIndex.forRow(myGrid, viewRowAndColumn.first);
      ViewIndex<GridColumn> viewColumn = ViewIndex.forColumn(myGrid, viewRowAndColumn.second);
      JComponent component = myRenderer.getComponent(viewRow, viewColumn, value);
      ResultViewWithCells view = ObjectUtils.tryCast(myGrid.getResultView(), ResultViewWithCells.class);
      if (view != null) ResultViewWithCells.prepareComponent(component, myGrid, view, viewRow, viewColumn, true);
      component.setRequestFocusEnabled(true);
      return component;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }
  }

  private static class ChooseBooleanActionGroup extends ActionGroup {
    final Object[] myValues;
    final TextBooleanCellEditor myEditor;

    ChooseBooleanActionGroup(Object @NotNull [] values, @NotNull DefaultBooleanEditorFactory.TextBooleanCellEditor editor) {
      myValues = values;
      myEditor = editor;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return JBIterable.of(myValues)
        .map(value -> new ChooseBooleanAction(value, myEditor))
        .toArray(new ChooseBooleanAction[0]);
    }
  }

  private static final class ChooseBooleanAction extends AnAction {
    final Object value;
    final TextBooleanCellEditor myEditor;

    private ChooseBooleanAction(@NotNull Object value, @NotNull DefaultBooleanEditorFactory.TextBooleanCellEditor editor) {
      this.value = value;
      myEditor = editor;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myEditor.setValue(value);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(value == ReservedCellValue.UNSET
                                  ? DataGridBundle.message("action.Console.TableResult.ClearCell.text")
                                  : StringUtil.toLowerCase(value.toString())); //NON-NLS
    }
  }

  private static class CheckboxBooleanCellEditor extends AbstractBooleanCellEditor {

    CheckboxBooleanCellEditor(@NotNull DataGrid grid,
                              @NotNull ModelIndex<GridRow> row,
                              @NotNull ModelIndex<GridColumn> column,
                              @NotNull Object value,
                              @Nullable Object typedValue) {
      super(grid, row, column, value, typedValue != null
                                      ? typedValue
                                      : Boolean.TRUE.equals(value)
                                        ? Boolean.FALSE
                                        : Boolean.TRUE);
    }

    @Override
    protected @NotNull JComponent createComponent(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @NotNull Object value) {
      DefaultBooleanRendererFactory.CheckboxBooleanRenderer myRenderer = new DefaultBooleanRendererFactory.CheckboxBooleanRenderer(myGrid);
      Disposer.register(this, myRenderer);
      Pair<Integer, Integer> viewRowAndColumn = myGrid.getRawIndexConverter().rowAndColumn2View().fun(row.asInteger(), column.asInteger());
      ViewIndex<GridRow> viewRow = ViewIndex.forRow(myGrid, viewRowAndColumn.first);
      ViewIndex<GridColumn> viewColumn = ViewIndex.forColumn(myGrid, viewRowAndColumn.second);
      JComponent component = myRenderer.getComponent(viewRow, viewColumn, value);
      ResultViewWithCells view = ObjectUtils.tryCast(myGrid.getResultView(), ResultViewWithCells.class);
      if (view != null) ResultViewWithCells.prepareComponent(component, myGrid, view, viewRow, viewColumn, true);
      component.setRequestFocusEnabled(true);
      return component;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }
  }
}
