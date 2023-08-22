/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

/**
 * <p>A IFilterObserver instance receives notifications when the associated
 * {@link IFilter} instance updates the held filter.</p>
 */
public interface IFilterObserver {

    /**
     * <p>Notification made by the observer when the associated {@link
     * IFilter} instance updates the held filter.</p>
     */
    void filterUpdated(IFilter obs);
}
