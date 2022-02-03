/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor.FilterEditor;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.text.Format;

/**
 * Interface implemented by the classes that handle the choices on each {@link
 * FilterEditor}.
 */
public abstract class ChoicesHandler implements TableModelListener, Runnable {

    protected AbstractFiltersHandler handler;

    /** The model being listened to handle model changes. */
    private TableModel listenedModel;

    /** this variable is true to signal an update to the FiltersHandler. */
    private boolean runScheduled;

    protected ChoicesHandler(AbstractFiltersHandler handler) {
        this.handler = handler;
    }

    /** Returns the {@link RowFilter} associated to this handler. */
    public abstract RowFilter getRowFilter();

    /**
     * Sets/unsets the handler on interrupt mode<br>
     * On interrupt mode, the associated {@link FiltersHandler} is likely to
     * send many update events, which shouldn't be treated (if possible).
     */
    public abstract boolean setInterrupted(boolean interrupted);

    /** Reports a {@link FilterEditor} update. */
    public abstract void editorUpdated(FilterEditor editor);

    /**
     * Reports a {@link IFilter} update.
     *
     * @param   retInfoRequired  set to true if the return value is required
     *
     * @return  true if the filter let pass any row
     */
    public abstract boolean filterUpdated(IFilter filter,
                                          boolean retInfoRequired);

    /**
     * Reports the beginning or end of {@link IFilter} add/remove operations.
     */
    public abstract void filterOperation(boolean start);

    /** Call triggered after a filter becomes enabled. */
    public abstract void filterEnabled(IFilter filter);

    /** Call triggered after all filters become disabled. */
    public abstract void allFiltersDisabled();

    /** Ensures that instant changes are propagated. */
    public abstract void consolidateFilterChanges(int modelIndex);

    /** Reports a table update. */
    protected abstract void tableUpdated(TableModel model,
                                         int        eventType,
                                         int        firstRow,
                                         int        lastRow,
                                         int        column);

    @Override public void tableChanged(TableModelEvent e) {
        int firstRow = e.getFirstRow();
        if (firstRow != TableModelEvent.HEADER_ROW) {
            int type = e.getType();
            TableModel model = (TableModel) e.getSource();
            tableUpdated(model, type, firstRow, e.getLastRow(), e.getColumn());
            if (!runScheduled) {
                runScheduled = true;
                // invoke later filtersHandler.tableUpdated, as perhaps the
                // row sorter hasn't been updated its status
                SwingUtilities.invokeLater(this);
            }
        }
    }

    /** {@link Runnable} interface. */
    @Override public void run() {
        runScheduled = false;
        handler.tableUpdated();
    }

    /**
     * Sets whether to send table model events to the {@link ChoicesHandler}.
     */
    protected void setEnableTableModelEvents(boolean set) {
        if (set) {
            JTable table = handler.getTable();
            if (table != null) {
                if (listenedModel != null) {
                    if (listenedModel == table.getModel()) {
                        return;
                    }

                    setEnableTableModelEvents(false);
                }

                listenedModel = table.getModel();
                listenedModel.addTableModelListener(this);
            }
        } else if (listenedModel != null) {
            listenedModel.removeTableModelListener(this);
            listenedModel = null;
        }
    }

    /**
     * Basic RowFilter.Entry instance, used internally to handle the RowFilter
     * default filtering.
     */
    static protected class RowEntry extends RowFilter.Entry {
        private final TableModel model;
        private final int count;
        private final Format[] formatters;
        public int row;

        public RowEntry(TableModel model, FilterEditor[] editors) {
            this.model = model;
            this.count = model.getColumnCount();

            int len = editors.length;
            formatters = new Format[len];
            while (len-- > 0) {
                formatters[len] = editors[len].getFormat();
            }
        }

        public int getModelRowCount() {
            return model.getRowCount();
        }

        public Format[] getFormatters() {
            return formatters;
        }

        @Override public Object getIdentifier() {
            return row;
        }

        @Override public TableModel getModel() {
            return model;
        }

        @Override public Object getValue(int index) {
            return model.getValueAt(row, index);
        }

        @Override public int getValueCount() {
            return count;
        }

        @Override public String getStringValue(int index) {
        	Format f = formatters[index];
            return f == null ? "" : f.format(getValue(index));
        }
    }

}
