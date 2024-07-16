/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter;
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor.FilterEditor;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableModel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Internal class to handle choices without adaptive behaviour<br>
 * Choices are automatically updated as the table model changes.
 */
class NonAdaptiveChoicesHandler extends ChoicesHandler {
    private boolean interrupted = true;

    NonAdaptiveChoicesHandler(FiltersHandler handler) {
        super(handler);
    }

    @Override public RowFilter getRowFilter() {
        return handler;
    }

    @Override public boolean setInterrupted(boolean interrupted) {
        if (this.interrupted != interrupted) {
            this.interrupted = interrupted;
            setEnableTableModelEvents(!interrupted);
            if (!interrupted) {
                for (FilterEditor editor : handler.getEditors()) {
                    editorUpdated(editor);
                }
            }
        }

        return !interrupted; // filter should be updated
    }

    @Override public void editorUpdated(FilterEditor editor) {
        if (editor.isEnabled()) {
            initEditorChoices(editor);
        }
    }

    @Override public boolean filterUpdated(IFilter iFilter,
                                           boolean retInfoRequired) {
        return true;
    }

    @Override public void filterOperation(boolean start) {
        handler.enableNotifications(!start);
    }

    @Override public void filterEnabled(IFilter filter) {
        for (FilterEditor editor : handler.getEditors()) {
            if (editor.getFilter() == filter) {
                initEditorChoices(editor);

                break;
            }
        }

        if (!interrupted) {
            setEnableTableModelEvents(true);
        }
    }

    @Override public void allFiltersDisabled() {
        setEnableTableModelEvents(false);
    }

    @Override public void consolidateFilterChanges(int modelIndex) {
        // nothing to do
    }

    @Override public void tableUpdated(TableModel model,
                                       int        eventType,
                                       int        firstRow,
                                       int        lastRow,
                                       int        column) {
        if (column != TableModelEvent.ALL_COLUMNS) {
            // a change in ONE column is always handled as an update
            // (every update is handled by re-extracting the choices
            FilterEditor editor = handler.getEditor(column);
            if (editor.isEnabled()) {
                setChoicesFromModel(editor);
            }
        } else {
            for (FilterEditor editor : handler.getEditors()) {
                if (editor.isEnabled()
                    && (AutoChoices.ENABLED == editor.getAutoChoices())) {
                    // insert events can be handled by adding the
                    // new model's values.
                    // updates/deletes require reparsing the whole
                    // table to obtain again the available choices
                    if (eventType == TableModelEvent.INSERT) {
                        editor.addChoices(new HashSet<>());
                    }
                    else {
                        setChoicesFromModel(editor);
                    }
                }
            }
        }
    }

    /**
     * Initializes the choices in the given editor.<br>
     * It can update the mode of the editor, from ENABLED to ENUMS (in case of
     * enumerations), and from ENUMS to DISABLED (for no enumerations)
     */
    private void initEditorChoices(FilterEditor editor) {
        AutoChoices autoChoices = editor.getAutoChoices();
        if (autoChoices == AutoChoices.DISABLED) {
            editor.setChoices(editor.getCustomChoices());
        } else {
            TableModel model = handler.getTable().getModel();
            Class<?> c = model.getColumnClass(editor.getModelIndex());
            boolean asEnum = c.equals(Boolean.class) || c.isEnum();
            if (asEnum && (autoChoices != AutoChoices.ENUMS)) {
                editor.setAutoChoices(AutoChoices.ENUMS);
            } else if (!asEnum && (autoChoices == AutoChoices.ENUMS)) {
                editor.setAutoChoices(AutoChoices.DISABLED);
            } else if (asEnum) {
                Set choices = editor.getCustomChoices();
                if (c.equals(Boolean.class)) {
                    choices.add(true);
                    choices.add(false);
                } else {
                    Collections.addAll(choices, c.getEnumConstants());
                }

                editor.setChoices(choices);
            } else {
                setChoicesFromModel(editor);
            }
        }
    }

    /** Sets the content for the given editor from the model's values. */
    private static void setChoicesFromModel(FilterEditor editor) {
        editor.setChoices(editor.getCustomChoices());
    }
}
