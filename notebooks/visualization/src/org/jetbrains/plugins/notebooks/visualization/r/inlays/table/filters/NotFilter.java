/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

import javax.swing.*;

/**
 * Composed set of filters, added via logical AND, and then NOT-ed the result.
 */
public class NotFilter extends AndFilter {

    /**
     * Default constructor.
     */
    public NotFilter() {
        super();
    }

    /**
     * Constructor built up out of one or more {@link
     * IFilter} instances.
     */
    public NotFilter(IFilter... observables) {
        super(observables);
    }

    /**
     * @see IFilter#include(RowFilter.Entry)
     */
    @Override
    public boolean include(RowFilter.Entry rowEntry) {
        return !isEnabled() || !super.include(rowEntry);
    }
}
