/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

import javax.swing.*;

/**
 * <p>Interface to be implemented by any instance holding a filter than can be
 * updated dynamically.</p>
 *
 * <p>Any change on the filter is propagated to the observers, in no given
 * order.</p>
 */
public interface IFilter {

    /** {@link RowFilter} interface. */
    boolean include(RowFilter.Entry rowEntry);

    /** Returns true if the filter is enabled. */
    boolean isEnabled();

    /** Enables/Disables the filter. */
    void setEnabled(boolean enable);

    /** Adds an observer to receive filter change notifications. */
    void addFilterObserver(IFilterObserver observer);

    /**
     * Unregisters an observer, that will not receive any further filter update
     * notifications.
     */
    void removeFilterObserver(IFilterObserver observer);
}
