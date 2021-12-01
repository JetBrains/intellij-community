/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

/**
 * Composed set of filters, added via logical AND.
 */
public class AndFilter extends ComposedFilter {

    /** Default constructor. */
    public AndFilter() {
        super();
    }

    /**
     * Constructor built up out of one or more {@link
     * IFilter} instances.
     */
    public AndFilter(IFilter... observables) {
        super(observables);
    }

    /** @see  IFilter#include(Entry) */
    @Override public boolean include(Entry rowEntry) {
        for (IFilter filter : filters) {
            if (filter.isEnabled() && !filter.include(rowEntry)) {
                return false;
            }
        }

        return true;
    }
}
