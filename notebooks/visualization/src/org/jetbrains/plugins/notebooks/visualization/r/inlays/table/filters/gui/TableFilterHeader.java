/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilterObserver;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IParser;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor.FilterEditor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.Format;
import java.util.*;


/**
 * <p>Implementation of a table filter that displays a set of editors associated
 * to each table's column. This is the main Gui component in this library.</p>
 *
 * <p>These editors are moved and resized as the table's columns are resized, so
 * this Swing component is better suited to be displayed atop, inline the {@link
 * JTable}, or just below, using the same size -and resizing- as the table
 * itself. The position can be automatically handled by the header itself -that
 * is the default behaviour-</p>
 *
 * <p>The editor associated to each column has the type {@link IFilterEditor},
 * and can be manipulated separately.</p>
 *
 * <p>The implementation relies on the {@link
 * FiltersHandler} class, please read its
 * documentation to understand the requirements on the table and its model, and
 * how it is affected by this filter</p>
 *
 * <p>The default settings can be modified by using system properties or by
 * setting values on the singleton {@link FilterSettings} instance</p>
 *
 * <p>Providing a filter header to an existing table is as easy as doing:</p>
 * <code>TableFilterHeader filter = new TableFilterHeader(table);</code>
 */
public class TableFilterHeader extends JPanel implements PropertyChangeListener {

    private static final long serialVersionUID = 5217701111228491294L;

    /** Minimum number of visible choices -if there are choices-. */
    private static final int MIN_VISIBLE_CHOICES = 4;

    /**
     * <p>Location of the header in relation to the table</p>
     *
     * <p>Note that this location is only meaningful when the table is set
     * inside a scroll pane, and this header instance is not explicitly included
     * in a container</p>
     *
     * <ul>
     *   <li>TOP: the filter is placed automatically above the table
     *     header.</li>
     *   <li>INLINE: the filter is placed below the table header, above the
     *     table's content.</li>
     *   <li>NONE: the filter is not automatically placed.</li>
     *   <li>REPLACE: the filter replaces the header (column names).</li>
     * </ul>
     */
    public enum Position {
        TOP, INLINE, NONE, REPLACE
    }

    /** The helper to handle the location of the filter in the table header. */
    private final PositionHelper positionHelper = new PositionHelper(this);

    /** Flag to handle instant filtering support. */
    boolean instantFilteringEnabled = FilterSettings.instantFiltering;

    /** Flag to handle allowance for vanishing during instant filtering */
    boolean instantVanishingEnabled = FilterSettings.allowInstantVanishing;

    /** Flag to handle auto completion support. */
    boolean autoCompletionEnabled = FilterSettings.autoCompletion;

    /** This is the total max number of visible rows (history PLUS choices). */
    int maxHistory = FilterSettings.maxPopupHistory;

    /** Setting to add / decrease height to the filter row. */
    int filterRowHeightDelta = FilterSettings.filterRowHeightDelta;

    /**
     * The columnsController is a glue component, controlling the filters
     * associated to each column.
     */
    FilterColumnsControllerPanel columnsController;

    /**
     * The privately owned instance of FiltersHandler that conforms the filter
     * defined by the TableFilterHeader.
     */
    AbstractFiltersHandler filtersHandler;

    /** The set of currently subscribed observers. */
    Set<IFilterHeaderObserver> observers = new HashSet<>();

    /** Helper to revalidate the controller when the table changes size. */
    private final ComponentAdapter resizer = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) {
            if (columnsController != null) {
                columnsController.revalidate();
            }
        }
    };

    /** Basic constructor, requires an attached table. */
    public TableFilterHeader() {
        this(null, null, null);
    }

    public TableFilterHeader(AbstractFiltersHandler filtersHandler) {
        this(null, filtersHandler);
    }

    ///** Basic constructor, using default {@link IParserModel}. */
    //public TableFilterHeader(JTable table) {
    //    this(table, null, null);
    //}
    //
    ///** Advanced constructor, enabling setting the {@link AutoChoices} mode */
    //public TableFilterHeader(JTable table, AutoChoices mode) {
    //    this(table, null, mode);
    //}
    //
    ///** Advanced constructor. */
    //public TableFilterHeader(JTable table, IParserModel parserModel) {
    //	this(table, parserModel, null);
    //}

    /** Full constructor. */
    public TableFilterHeader(JTable table, AbstractFiltersHandler filtersHandler) {
        super(new BorderLayout());

        setOpaque(false);
        setBackground(Gray.TRANSPARENT);

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setBackground(Gray.TRANSPARENT);

        setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

        add(panel, BorderLayout.CENTER); // do not take all width
        this.filtersHandler = filtersHandler;
        setPosition(FilterSettings.headerPosition);
        setTable(table);
    }

    public TableFilterHeader(JTable table, IParserModel parserModel, AutoChoices mode) {
        this(table, new FiltersHandler(mode == null ? FilterSettings.autoChoices : mode,
                                       parserModel == null ? FilterSettings.newParserModel() : parserModel));
    }

    /** Returns the filter editor for the given column in the table model. */
    public IFilterEditor getFilterEditor(int modelColumn) {
        return (columnsController == null)
            ? null
            : columnsController.getFilterEditor(getTable()
                    .convertColumnIndexToView(modelColumn));
    }

    /** Required to track model changes on the table */
    @Override public void propertyChange(PropertyChangeEvent evt) {
    	if ("model".equals(evt.getPropertyName())){
    		recreateController();
    	} else if ("componentOrientation".equals(evt.getPropertyName())){
            recreateController();
        }
    }

    /**
     * <p>Attaches the table where the filtering will be applied.</p>
     *
     * <p>It will be created a row of editors, that follow the size and position
     * of each of the columns in the table.</p>
     *
     * <p>Setting the parameter to null effectively de-associates the
     * TableFilterHeader from any previously associated table -which, unless
     * the {@link Position} is set to NONE, also implies removing the filter
     * header from the GUI-.</p>
     */
    public void setTable(JTable table) {
        filtersHandler.enableNotifications(false);

        JTable oldTable = getTable();
        positionHelper.changeTable(oldTable, table);
        if (oldTable != null) {
            oldTable.removeComponentListener(resizer);
            oldTable.removePropertyChangeListener("model", this);
            oldTable.removePropertyChangeListener("componentOrientation", this);
        }

        filtersHandler.setTable(table);
        if (table == null) {
            removeController();
            revalidate();
        } else {
            recreateController();
            table.addComponentListener(resizer);
            table.addPropertyChangeListener("model", this);
            table.addPropertyChangeListener("componentOrientation", this);
        }

        filtersHandler.enableNotifications(true);
    }

    /** Returns the table currently attached. */
    public JTable getTable() {
        return (filtersHandler == null) ? null : filtersHandler.getTable();
    }

    /**
     * Sets the {@link IParserModel}, used to define the parsing of text on the
     * filter editors.
     */
    public void setParserModel(IParserModel parserModel) {
        filtersHandler.setParserModel(parserModel);
    }

    /**
     * Retrieves the current {@link IParserModel}; The returned reference is
     * required to update properties like {@link Format} or {@link Comparator}
     * instances associated to each class, or whether to ignore case.
     */
    public IParserModel getParserModel() {
        return filtersHandler.getParserModel();
    }

    /**
     * Sets the auto choices flag. When set, all editors are automatically
     * populated with choices extracted from the table's content -and updated as
     * the table is updated-.
     */
    public void setAutoChoices(AutoChoices set) {
        filtersHandler.setAutoChoices(set);
    }

    /** Returns the auto choices flag. */
    public AutoChoices getAutoChoices() {
        return filtersHandler.getAutoChoices();
    }

    /** Sets the adaptive choices mode. */
    public void setAdaptiveChoices(boolean enable) {
        filtersHandler.setAdaptiveChoices(enable);
    }

    /** Returns the adaptive choices mode. */
    public boolean isAdaptiveChoices() {
        return filtersHandler.isAdaptiveChoices();
    }

    /**
     * Enables instant filtering, as the user edits the filter's text<br>
     * The exact way the instant filtering works depends on the associated.
     *
     * @see  IParser#parseInstantText(String)
     */
    public void setInstantFiltering(boolean enable) {
        if (this.instantFilteringEnabled != enable) {
            this.instantFilteringEnabled = enable;
            if (columnsController != null) {
                for (FilterEditor fe : columnsController) {
                    fe.setInstantFiltering(enable);
                }
            }
        }
    }

    /** Returns true if instant filtering is enabled. */
    public boolean isInstantFiltering() {
        return this.instantFilteringEnabled;
    }

    /**
     * Enables vanishing during instant filtering<br>
     * If enabled, entering a filter expression that produces no rows
     * will hide; otherwise, the filter is just marked with warning color.
     */
    public void setAllowedInstantVanishing(boolean enable) {
        if (this.instantVanishingEnabled != enable) {
            this.instantVanishingEnabled = enable;
            if (columnsController != null) {
                for (FilterEditor fe : columnsController) {
                    fe.setAllowedInstantVanishing(enable);
                }
            }
        }
    }

    /** Returns true if vanishing is enabled during instant filtering */
    public boolean isAllowedInstantVanishing() {
        return this.instantVanishingEnabled;
    }

    /** Enables auto completion, as the user edits the filter's text. */
    public void setAutoCompletion(boolean enable) {
        if (this.autoCompletionEnabled != enable) {
            this.autoCompletionEnabled = enable;
            if (columnsController != null) {
                for (FilterEditor fe : columnsController) {
                    fe.setAutoCompletion(enable);
                }
            }
        }
    }

    /** Returns true if auto completion is enabled. */
    public boolean isAutoCompletion() {
        return this.autoCompletionEnabled;
    }

    /** Enables / Disables auto selection mode */
    public void setAutoSelection(boolean enable) {
        filtersHandler.setAutoSelection(enable);
    }

    /** Returns true if auto selection is enabled. */
    public boolean isAutoSelection() {
        return filtersHandler.isAutoSelection();
    }

    /**
     * Sets the filter on updates flag.<br>
     * It sets the sortOnUpdates flag on the underlying {@link DefaultRowSorter}
     * it is, in fact, just a helper to set this flag without accessing directly
     * the row sorter.
     *
     * @see  DefaultRowSorter#setSortsOnUpdates(boolean)
     */
    public void setFilterOnUpdates(boolean enable) {
        filtersHandler.setFilterOnUpdates(enable);
    }

    /** Returns true if the filter is reapplied on updates. */
    public boolean isFilterOnUpdates() {
        return filtersHandler.isFilterOnUpdates();
    }

    /** Hides / makes visible the header. */
    @Override public void setVisible(boolean flag) {
        if (isVisible() != flag) {
            positionHelper.headerVisibilityChanged(flag);
        }

        super.setVisible(flag);
        positionHelper.headerVisibilityChanged(flag);
    }

    /** Enables/Disables the filters. */
    @Override public void setEnabled(boolean enabled) {
        // it is not possible to call to super.setEnabled(enabled);
        // the filter header can embed the the header of the table, which
        // would then become also disabled.
        if (filtersHandler != null) {
            filtersHandler.setEnabled(enabled);
        }
    }

    /** Returns the current enable status. */
    @Override public boolean isEnabled() {
        return (filtersHandler == null) || filtersHandler.isEnabled();
    }

    /** Sets the position of the header related to the table. */
    public void setPosition(Position location) {
        positionHelper.setPosition(location);
    }

    /** Returns the mode currently associated to the TableHeader. */
    public Position getPosition() {
        return positionHelper.getPosition();
    }

    /**
     * Sets the maximum history size, always lower than the max number of
     * visible rows.
     */
    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
        if (columnsController != null) {
            for (FilterEditor fe : columnsController) {
                fe.setMaxHistory(maxHistory);
            }
        }
    }

    /** Returns the maximum history size. */
    public int getMaxHistory() {
        return maxHistory;
    }

    /** Adds a filter -user specified- to the filter header. */
    public void addFilter(IFilter... filter) {
        filtersHandler.addFilter(filter);
    }

    /** Adds a filter -user specified- to the filter header. */
    public void removeFilter(IFilter... filter) {
        filtersHandler.removeFilter(filter);
    }

    /** Adds a new observer to the header. */
    public void addHeaderObserver(IFilterHeaderObserver observer) {
        observers.add(observer);
    }

    /** Removes an existing observer from the header. */
    public void removeHeaderObserver(IFilterHeaderObserver observer) {
        observers.remove(observer);
    }

    /**
     * <p>Invokes resetFilter on all the editor filters.</p>
     *
     * @see  IFilterEditor#resetFilter()
     */
    public void resetFilter() {

        if (columnsController != null) {
            filtersHandler.enableNotifications(false);
            for (FilterEditor fe : columnsController) {
                fe.resetFilter();
            }

            filtersHandler.enableNotifications(true);
        }
    }


    /** Method automatically invoked when the class ancestor changes. */
    @Override public void addNotify() {
        super.addNotify();
        positionHelper.filterHeaderContainmentUpdate();
    }

    /**
     * removes the current columnsController.
     *
     * @return  true if there was a controller
     */
    private boolean removeController() {

        if (columnsController != null) {
            columnsController.detach();
            remove(columnsController);
            columnsController = null;

            return true;
        }

        return false;
    }

    /** creates/recreates the current columnsController. */
    void recreateController() {
        filtersHandler.enableNotifications(false);
        removeController();
        columnsController = new FilterColumnsControllerPanel(getFont(), getForeground(), getBackground());
        add(columnsController, BorderLayout.WEST);
        revalidate();
        filtersHandler.updateModel();
        filtersHandler.enableNotifications(true);
    }

    /**
     * Class setting up together all the column filters<br>
     * Note that, while the TableFilterHeader handles columns using their model
     * numbering, the FilterColumnsControllerPanel manages the columns as they
     * are sorted in the Table. That is, if the user changes the order of two or
     * more columns, this class reacts by reordering internal data structures
     */
    private class FilterColumnsControllerPanel extends JPanel
        implements TableColumnModelListener, Runnable, Iterable<FilterEditor> {

        private static final long serialVersionUID = -5183169239497633085L;

        /** The list of columns, sorted in the view way. */
        private final LinkedList<FilterColumnPanel> columns;

        /** Preferred size of the component. */
        private final Dimension preferredSize;

        /**
         * The panel must keep a reference to the TableColumnModel, to be able
         * to 'unregister' when the controller is destroyed.
         */
        private final TableColumnModel tableColumnModel;

        /**
         * Variable keeping track of the number of times that the run() method
         * is going to be invoked from the gui thread.
         */
        private int autoRun;

        /**
         * When a new model is set, all columns are first removed, and the new
         * ones then created. While columns are removed, the state of the filter
         * (filtersHandler) can changed between enabled and not enabled, but it
         * is needed to remember the state at the start of the cycle, to create
         * the new editors with the expected enable state.
         */
        private Boolean handlerEnabled;

        /**
         * The model associated to the table when the controller is created.
         */
        private final TableModel tableModel;

        /**
         * Creates the controller for all the columns<br>
         * It will automatically create an editor of the current EditorMode for
         * each column.
         */
        public FilterColumnsControllerPanel(Font  font,
                                            Color foreground,
                                            Color background) {
            super(null);
            super.setFont(font);


            super.setForeground(foreground);

            setOpaque(false);
            setBackground(Gray.TRANSPARENT);

         //   super.setBackground(background);

            this.tableColumnModel = getTable().getColumnModel();
            this.tableModel = getTable().getModel();

            boolean enabled = filtersHandler.isEnabled();
            int count = tableColumnModel.getColumnCount();
            columns = new LinkedList<>();
            for (int i = 0; i < count; i++) {
                createColumn(i, enabled);
            }
            preferredSize = new Dimension(0,
                    (count == 0) ? 0
                                 : (columns.get(0).h + filterRowHeightDelta));
            placeComponents();
            tableColumnModel.addColumnModelListener(this);
        }

        /** {@link Iterable} interface. */
        @Override public @NotNull Iterator<FilterEditor> iterator() {
            final Iterator<FilterColumnPanel> it = columns.iterator();

            return new Iterator<>() {

              @Override
              public void remove() {
                // not supported
              }

              @Override
              public FilterEditor next() {
                return it.next().editor;
              }

              @Override
              public boolean hasNext() {
                return it.hasNext();
              }
            };
        }

        /** Creates the FilterColumnPanel for the given column number. */
        private void createColumn(int columnView, boolean enableIt) {
            int columnModel = getTable().convertColumnIndexToModel(columnView);
            FilterEditor editor = createEditor(columnModel, enableIt);
            FilterColumnPanel column = new FilterColumnPanel(
                    tableColumnModel.getColumn(columnView), editor);
            column.updateHeight();
            columns.add(column);
            add(column);
        }

        /** Creates an editor for the given column. */
        private FilterEditor createEditor(int modelColumn, boolean enableIt) {
            FilterEditor ret = new FilterEditor(filtersHandler, modelColumn,
                    getTable().getModel().getColumnClass(modelColumn));
            ret.setMaxHistory(maxHistory);
            ret.setInstantFiltering(instantFilteringEnabled);
            ret.setAutoCompletion(autoCompletionEnabled);
            ret.getFilter().setEnabled(enableIt);
            filtersHandler.addFilterEditor(ret);

            return ret;
        }

        /** Detaches the current instance from any registered listeners. */
        public void detach() {

            for (FilterColumnPanel column : columns) {
                column.detach();
            }

            tableColumnModel.removeColumnModelListener(this);
        }

        /**
         * Returns the editor for the given column, or null if such column does
         * not exist.
         */
        public FilterEditor getFilterEditor(int viewColumn) {
            return (columns.size() > viewColumn)
                ? columns.get(viewColumn).editor : null;
        }

        /** Computes the proper preferred height -width is not important-. */
        public void updateHeight() {
            int h = 0;

            for (FilterColumnPanel c : columns) {
                h = Math.max(h, c.h);
            }

            preferredSize.height = h + filterRowHeightDelta;
            placeComponents();
            repaint();
        }

        /** {@link TableColumnModelListener} interface. */
        @Override public void columnMarginChanged(ChangeEvent e) {
            placeComponents();
        }

        /** {@link TableColumnModelListener} interface. */
        @Override public void columnMoved(TableColumnModelEvent e) {
            if (e.getFromIndex() != e.getToIndex()) {
                FilterColumnPanel fcp = columns.remove(e.getFromIndex());
                columns.add(e.getToIndex(), fcp);
                placeComponents();
            }
            // previous block places each filter column in the right position
            // BUT does not take in consideration the dragging distance
            JTableHeader header = getTable().getTableHeader();
            TableColumn tc = header.getDraggedColumn();
            if (tc != null) {
                boolean rightToLeft = getTable().getComponentOrientation() ==
                        ComponentOrientation.RIGHT_TO_LEFT;
                // Iterate the filter columns, we need to know the previous
                // and the current column
                Iterator<FilterColumnPanel> it = rightToLeft?
                        columns.descendingIterator() :
                        columns.iterator();
                FilterColumnPanel previous = null;
                while (it.hasNext()) {
                    FilterColumnPanel fcp = it.next();
                    if (fcp.tc == tc){
                        Rectangle r = null;
                        double x = 0;
                        if (previous != null) {
                            r = previous.getBounds();
                            // obtain on X the position that the current
                            // dragged column should be IF there would be no dragging
                            // (previous panel plus its width)
                            x = r.getX() + r.getWidth();
                        }
                        // shift now the column to the correct distance
                        r = fcp.getBounds(r);
                        r.translate((int)(x - r.getX() + header.getDraggedDistance()), 0);
                        fcp.setBounds(r);

                        // one detail is left: the Z order of this column should be lower
                        // that the Z order of the column being dragged over
                        if (rightToLeft) {
                            // in this case, previous is the next column, not the one before!
                            previous = it.hasNext()? it.next() : null;
                        }
                        if (previous != null) {
                            int prevZOrder = getComponentZOrder(previous);
                            int zOrder = getComponentZOrder(fcp);
                            boolean overPreviousDragging =  rightToLeft?
                                    header.getDraggedDistance() > 0 :
                                    header.getDraggedDistance() < 0;
                            if (overPreviousDragging != (zOrder < prevZOrder)) {
                                setComponentZOrder(previous, zOrder);
                                setComponentZOrder(fcp, prevZOrder);
                            }
                        }
                        break;
                    }
                    previous = fcp;
                }
            }
        }

        /** {@link TableColumnModelListener} interface. */
        @Override public void columnAdded(TableColumnModelEvent e) {

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
	            createColumn(e.getToIndex(), handlerEnabled);
	            update();
        	}
        }

        /** {@link TableColumnModelListener} interface. */
        @Override public void columnRemoved(TableColumnModelEvent e) {

        	//Support the case where a model is being changed
        	if (isCorrectModel()) {
	            // see the comment on columnAdded
	            filtersHandler.enableNotifications(false);
	            if (handlerEnabled == null) {
	                handlerEnabled = filtersHandler.isEnabled();
	            }
                FilterColumnPanel fcp = columns.remove(e.getFromIndex());
	            fcp.detach();
	            remove(fcp);
	            update();
        	}
        }

        /** {@link TableColumnModelListener} interface. */
        @Override public void columnSelectionChanged(ListSelectionEvent e) {
            // nothing needed here
        }

        private boolean isCorrectModel() {
        	JTable table = getTable();
        	return table != null && tableModel == table.getModel();
        }

        /**
         * Updates the columns. If this is the GUI thread, better wait until all
         * the events have been handled. Otherwise, do it immediately, as it is
         * not known how the normal/Gui thread can interact
         */
        private void update() {
            autoRun += 1;
            if (SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(this);
            } else {
                run();
            }
        }

        @Override public void run() {
            // see the comment on columnAdded
            if ((--autoRun == 0) && (getTable() != null)) {
                handlerEnabled = null;
                updateHeight();
            }

            filtersHandler.enableNotifications(true);
        }

        /**
         * Places all the components in line, respecting their preferred widths.
         */
        void placeComponents() {
            int x = 0;
            Iterator<FilterColumnPanel> it =
                    getTable().getComponentOrientation() ==
                            ComponentOrientation.RIGHT_TO_LEFT?
                    columns.descendingIterator() :
                    columns.iterator();

            while (it.hasNext()) {
                FilterColumnPanel fcp = it.next();
                fcp.setBounds(x, 0, fcp.w, preferredSize.height);
                x += fcp.w;
            }
            revalidate();
        }

        @Override public Dimension getPreferredSize() {
            JTable table = getTable();
            if (table != null) {
                preferredSize.width = table.getWidth();
            }

            return preferredSize;
        }

        /**
         * Class controlling the filter applied to one specific column<br>
         * It resizes itself automatically as the associated table column is
         * resized.
         */
        private class FilterColumnPanel extends JPanel
            implements PropertyChangeListener, IFilterObserver {

            private static final long serialVersionUID = 6858728575542289815L;

            /** The associated editor. */
            FilterEditor editor;

            /** Dimensions of the component. */
            int w;
            int h;

            /**
             * The TableColumn object, to which is registered to get property
             * changes, in order to keep the same width.
             */
            TableColumn tc;

            /** Constructor. */
            public FilterColumnPanel(TableColumn tc, FilterEditor editor) {
                super(new BorderLayout());

                setOpaque(false);
                setBackground(Gray.TRANSPARENT);

                this.tc = tc;
                w = tc.getWidth();
                add(editor, BorderLayout.CENTER);
                h = getPreferredSize().height;
                editor.getFilter().addFilterObserver(this);
                for (IFilterHeaderObserver observer : observers) {
                    observer.tableFilterEditorCreated(TableFilterHeader.this,
                        editor, tc);
                }

                this.editor = editor;
                tc.addPropertyChangeListener(this);
            }

            /**
             * Performs any cleaning required before removing this component.
             */
            public void detach() {

                if (editor != null) {
                    filtersHandler.removeFilterEditor(editor);
                    remove(editor);
                    editor.getFilter().removeFilterObserver(this);
                    for (IFilterHeaderObserver observer : observers) {
                        observer.tableFilterEditorExcluded(
                            TableFilterHeader.this, editor, tc);
                    }
                }

                tc.removePropertyChangeListener(this);
            }

            public void updateHeight() {
                h = getPreferredSize().height;
                revalidate();
            }

            @Override public void filterUpdated(IFilter obs) {
                if (editor != null) { // avoid sending the first update
                    for (IFilterHeaderObserver observer : observers) {
                        observer.tableFilterUpdated(TableFilterHeader.this,
                            editor, tc);
                    }
                }
            }

            /** Listening for changes on the width of the table' column. */
            @Override public void propertyChange(PropertyChangeEvent evt) {
                // just listen for any property
                int newW = tc.getWidth();

                if (w != newW) {
                    w = newW;
                    placeComponents();
                }
            }
        }
    }
}
