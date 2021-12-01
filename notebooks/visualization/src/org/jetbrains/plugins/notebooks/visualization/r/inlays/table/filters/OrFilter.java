/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

import javax.swing.*;

/**
 * Composed set of filters, added via logical OR.
 */
public class OrFilter extends ComposedFilter {

    /** Default constructor. */
    public OrFilter() {
        super();
    }

    /**
     * Constructor built up out of one or more {@link
     * IFilter} instances.
     */
    public OrFilter(IFilter... observables) {
        super(observables);
    }

    /** @see  IFilter#include(RowFilter.Entry) */
    @Override public boolean include(RowFilter.Entry rowEntry) {
        boolean ret = true;
        for (IFilter filter : filters) {
            if (filter.isEnabled()) {
                if (filter.include(rowEntry)) {
                    return true;
                }

                ret = false;
            }
        }

        return ret;
    }
}
