/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>Abstract parent class to support the composition of multiple filters.</p>
 *
 * <p>The exact composition semantics (and / or / not) are not defined.</p>
 */
abstract public class ComposedFilter extends Filter implements IFilterObserver {

    /** Set of associated IFilters. */
    protected Set<IFilter> filters;

    /** disabled filters. */
    private Set<IFilter> disabledFilters = new HashSet<>();

    /** Default constructor. */
    protected ComposedFilter() {
        filters = new HashSet<>();
    }

    /**
     * Constructor built up out of one or more {@link
     * IFilter} instances.
     */
    protected ComposedFilter(IFilter... observables) {
        this();
        addFilter(observables);
    }

    /**
     * Subscribes one or more {@link IFilter} instances to
     * receive filter events from this composition filter.
     */
    public void addFilter(IFilter... filtersToAdd) {
        for (IFilter filter : filtersToAdd) {
            if (filters.add(filter)) {
                filter.addFilterObserver(this);
                if (filter.isEnabled()) {
                    super.setEnabled(true);
                } else {
                    disabledFilters.add(filter);
                }
            }
        }
    }

    /**
     * Unsubscribes one or more {@link IFilter}s that were
     * previously subscribed to receive filter events.
     */
    public void removeFilter(IFilter... filtersToRemove) {
        boolean report = false;
        for (IFilter filter : filtersToRemove) {
            if (filters.remove(filter)) {
                filter.removeFilterObserver(this);
                disabledFilters.remove(filter);
                report = true;
            }
        }

        if (report) {
            if (isEnabled() && !filters.isEmpty()
                    && (disabledFilters.size() == filters.size())) {
                super.setEnabled(false);
            } else {
                reportFilterUpdatedToObservers();
            }
        }
    }

    /**
     * Returns all {@link IFilter} instances previously
     * added.
     */
    public Set<IFilter> getFilters() {
        return new HashSet<>(filters);
    }

    /** @see  IFilterObserver#filterUpdated(IFilter) */
    @Override public void filterUpdated(IFilter filter) {
        boolean enabled = isEnabled();
        boolean changeState = false;
        if (filter.isEnabled()) {
            changeState = disabledFilters.remove(filter) && !enabled;
        } else {
            changeState = disabledFilters.add(filter)
                    && (disabledFilters.size() == filters.size());
        }

        if (changeState) {
            super.setEnabled(!enabled);
        } else {
            reportFilterUpdatedToObservers();
        }
    }

    /** @see  IFilter#setEnabled(boolean) */
    @Override public void setEnabled(boolean enable) {
        if (filters.isEmpty()) {
            super.setEnabled(enable);
        } else {
            // perhaps some filter will not honor the request
            // super.setEnabled is now only call when the filters report
            // its update
            for (IFilter filter : filters) {
                filter.setEnabled(enable);
            }
        }
    }

    /** Returns true if there is information of this filter as disabled. */
    protected boolean isDisabled(IFilter filter) {
        return disabledFilters.contains(filter);
    }

}
