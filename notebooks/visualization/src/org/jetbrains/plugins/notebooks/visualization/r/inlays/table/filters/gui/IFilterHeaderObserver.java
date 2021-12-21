/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import javax.swing.table.TableColumn;

/**
 * <p>A ITableFilterHeaderObserver instance receives notifications when the
 * associated {@link IFilterEditor} instances are
 * created, destroyed, or update the held filter.</p>
 */
public interface IFilterHeaderObserver {

    /**
     * <p>Informs the observer than a new filter editor is created</p>
     *
     * @param  header       the associated table filter header
     * @param  editor
     * @param  tableColumn  the associated {@link TableColumn}
     */
    void tableFilterEditorCreated(TableFilterHeader header,
                                  IFilterEditor editor,
                                  TableColumn tableColumn);

    /**
     * <p>Informs the observer than an existing filter editor has been excluded
     * from the filter header</p>
     *
     * @param  header       the associated table filter header
     * @param  editor
     * @param  tableColumn  the associated {@link TableColumn}
     */
    void tableFilterEditorExcluded(TableFilterHeader header,
                                   IFilterEditor editor,
                                   TableColumn tableColumn);

    /**
     * <p>Notification made by the {@link IFilterEditor} when the filter's content is
     * updated</p>
     *
     * @param  header       the associated table filter header
     * @param  editor       the observable instance
     * @param  tableColumn  the associated {@link TableColumn}
     */
    void tableFilterUpdated(TableFilterHeader header,
                            IFilterEditor editor,
                            TableColumn tableColumn);
}
