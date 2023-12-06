package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;


import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.table.AdditionalTableHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilterObserver;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor.FilterEditor;

import javax.swing.*;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TableFilterHeader extends AdditionalTableHeader {
  /** Flag to handle instant filtering support. */
  private static final boolean instantFilteringEnabled = FilterSettings.instantFiltering;

  /** Flag to handle auto completion support. */
  private static final boolean autoCompletionEnabled = FilterSettings.autoCompletion;

  /** This is the total max number of visible rows (history PLUS choices). */
  private static final int maxHistory = FilterSettings.maxPopupHistory;

  /** Setting to add / decrease height to the filter row. */
  private static final int filterRowHeightDelta = FilterSettings.filterRowHeightDelta;

  /**
   * The privately owned instance of FiltersHandler that conforms the filter
   * defined by the TableFilterHeader.
   */
  private final AbstractFiltersHandler filtersHandler;

  /** The set of currently subscribed observers. */
  private final Set<IFilterHeaderObserver> observers = new HashSet<>();

  /** Basic constructor, requires an attached table. */
  public TableFilterHeader() {
    this(null, null, null);
  }

  public TableFilterHeader(AbstractFiltersHandler filtersHandler) {
    this(null, filtersHandler);
  }

  /** Full constructor. */
  public TableFilterHeader(JTable table, AbstractFiltersHandler filtersHandler) {
    setOpaque(false);
    setBackground(Gray.TRANSPARENT);

    JPanel panel = new JPanel();
    panel.setOpaque(false);
    panel.setBackground(Gray.TRANSPARENT);

    setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    add(panel, BorderLayout.CENTER); // do not take all width
    this.filtersHandler = filtersHandler;
    setPosition(FilterSettings.headerPosition);

    installTable(table);
  }

  public TableFilterHeader(JTable table, IParserModel parserModel, AutoChoices mode) {
    this(table,
         new FiltersHandler(mode == null ? FilterSettings.autoChoices : mode,
                            parserModel == null ? FilterSettings.newParserModel() : parserModel));
  }

  /** Returns the filter editor for the given column in the table model. */
  public IFilterEditor getFilterEditor(int modelColumn) {
    return (getColumnsController() == null)
           ? null
           : ((FilterColumnsControllerPanel)getColumnsController()).getFilterEditor(getTable().convertColumnIndexToView(modelColumn));
  }

  /** Returns the table currently attached. */
  @Nullable
  @Override
  public JTable getTable() {
    return filtersHandler == null ? null : filtersHandler.getTable();
  }

  @Override
  protected void setTable(@Nullable JTable table) {
    filtersHandler.setTable(table);
  }

  @Override
  public void installTable(@Nullable JTable table) {
    filtersHandler.enableNotifications(false);
    super.installTable(table);
    filtersHandler.enableNotifications(true);
  }

  /** Sets the adaptive choices mode. */
  public void setAdaptiveChoices(boolean enable) {
    filtersHandler.setAdaptiveChoices(enable);
  }

  @Override
  public void detachController() {
    getColumnsController().detach();
  }

  @NotNull
  @Override
  public ColumnsControllerPanel createColumnsController() {
    JTable currentTable = Objects.requireNonNull(getTable());
    return new TableFilterHeader.FilterColumnsControllerPanel(currentTable, getFont(), getForeground());
  }

  class FilterColumnsControllerPanel extends ColumnsControllerPanel {
    /**
     * When a new model is set, all columns are first removed, and the new
     * ones then created. While columns are removed, the state of the filter
     * (filtersHandler) can be changed between enabled and not enabled, but it
     * is needed to remember the state at the start of the cycle, to create
     * the new editors with the expected enabled state.
     */
    private Boolean handlerEnabled;

    /**
     * Creates the controller for all the columns<br>
     * It will automatically create an editor of the current EditorMode for
     * each column.
     */
    FilterColumnsControllerPanel(@NotNull JTable table,
                                 Font font,
                                 Color foreground) {
      super(table);
      super.setFont(font);

      super.setForeground(foreground);

      setOpaque(false);
      setBackground(Gray.TRANSPARENT);

      int count = getTableColumnModel().getColumnCount();
      for (int i = 0; i < count; i++) {
        createColumn(i);
      }

      setMyPreferredSize(computeMyPreferredSize());
      placeComponents();
      getTableColumnModel().addColumnModelListener(this);
    }

    @NotNull
    @Override
    public FilterColumnPanel createColumn(int columnView) {
      boolean enabled = filtersHandler.isEnabled();
      int columnModel = getTable().convertColumnIndexToModel(columnView);
      FilterEditor editor = createEditor(columnModel, enabled);
      FilterColumnPanel column = new FilterColumnPanel(getTableColumnModel().getColumn(columnView), editor);
      column.updateHeight();
      getColumns().add(column);
      add(column);
      return column;
    }

    /** Creates an editor for the given column. */
    private FilterEditor createEditor(int modelColumn, boolean enableIt) {
      FilterEditor ret = new FilterEditor(filtersHandler,
                                          modelColumn,
                                          getTable().getModel().getColumnClass(modelColumn));
      ret.setMaxHistory(maxHistory);
      ret.setInstantFiltering(instantFilteringEnabled);
      ret.setAutoCompletion(autoCompletionEnabled);
      ret.getFilter().setEnabled(enableIt);
      filtersHandler.addFilterEditor(ret);

      return ret;
    }

    /**
     * Returns the editor for the given column, or null if such column does
     * not exist.
     */
    public FilterEditor getFilterEditor(int viewColumn) {
      return (getColumns().size() > viewColumn)
             ? ((FilterColumnPanel)getColumns().get(viewColumn)).editor
             : null;
    }

    // previously it was run method. update method is in the father
    @Override
    public void updateColumns() {
      // see the comment on columnAdded
      if ((--autoRun == 0)) {
        getTable();
        handlerEnabled = null;
        updateHeight();
      }

      filtersHandler.enableNotifications(true);
    }

    /** Computes the proper preferred height-width is not important-. */
    public void updateHeight() {
      int h = 0;

      for (AdditionalPanel c : getColumns()) {
        h = Math.max(h, c.getMyHeight());
      }

      myPreferredSize.height = h + filterRowHeightDelta;

      placeComponents();
      repaint();
    }

    /** {@link TableColumnModelListener} interface. Here we add handlerEnable processing. */
    @Override public void columnAdded(@NotNull TableColumnModelEvent e) {

      //Support the case where a model is being changed
      if (isCorrectModel()) {

        // when adding or removing columns to the table model, or, in
        // general, when fireTableStructureChanged() is invoked on a
        // table model, all columns are removed, and the definitive
        // ones added.
        // To avoid sending update notifications to the table, which
        // may be quite time/CPU consuming, it is better to disable
        // the notifications and only send them after all columns
        // have been added or removed.
        // As there is no way to know when the last column is added
        // (or removed), the implementation disables the
        // notifications and request to be auto called eventually.
        // This call (run()) will happen when all the column
        // modifications have concluded, so then it is safe to
        // reactivate the notifications
        filtersHandler.enableNotifications(false);
        if (handlerEnabled == null) {
          handlerEnabled = filtersHandler.isEnabled();
        }
      }

      super.columnAdded(e);
    }

    /** {@link TableColumnModelListener} interface. Here we add handlerEnable processing. */
    @Override public void columnRemoved(@NotNull TableColumnModelEvent e) {
      //Support the case where a model is being changed
      if (isCorrectModel()) {
        // see the comment on columnAdded
        filtersHandler.enableNotifications(false);
        if (handlerEnabled == null) {
          handlerEnabled = filtersHandler.isEnabled();
        }
      }

      super.columnRemoved(e);
    }

    @NotNull
    @Override
    public Dimension computeMyPreferredSize() {
      int count = getTableColumnModel().getColumnCount();
      return new Dimension(0,
                           (count == 0) ? 0 : (getColumns().get(0).getMyHeight() + filterRowHeightDelta));
    }

    @Override public Dimension getPreferredSize() {
      JTable table = getTable();
      myPreferredSize.width = table.getWidth();

      return myPreferredSize;
    }

    /**
     * Class controlling the filter applied to one specific column<br>
     * It resizes itself automatically as the associated table column is
     * resized.
     */
    class FilterColumnPanel extends AdditionalPanel implements IFilterObserver {

      /** The associated editor. */
      public FilterEditor editor;

      /** Constructor. */
      FilterColumnPanel(TableColumn tc, FilterEditor editor) {
        super(tc);

        setOpaque(false);
        setBackground(Gray.TRANSPARENT);

        setMyWidth(tc.getWidth());
        setMyHeight(getPreferredSize().height);

        add(editor, BorderLayout.CENTER);
        editor.getFilter().addFilterObserver(this);

        for (IFilterHeaderObserver observer : observers) {
          observer.tableFilterEditorCreated(TableFilterHeader.this,
                                            editor, tc);
        }

        this.editor = editor;
        tc.addPropertyChangeListener(this);
      }

      @Override
      public void detach() {
        if (editor != null) {
          filtersHandler.removeFilterEditor(editor);
          remove(editor);
          editor.getFilter().removeFilterObserver(this);
          for (IFilterHeaderObserver observer : observers) {
            observer.tableFilterEditorExcluded(
              TableFilterHeader.this, editor, getTableColumn());
          }
        }

        getTableColumn().removePropertyChangeListener(this);

      }

      @Override
      public void updateHeight() {
        setMyHeight(getPreferredSize().height);
        revalidate();
      }

      @Override public void filterUpdated(IFilter obs) {
        if (editor != null) { // avoid sending the first update
          for (IFilterHeaderObserver observer : observers) {
            observer.tableFilterUpdated(TableFilterHeader.this, editor, getTableColumn());
          }
        }
      }
    }
  }
}