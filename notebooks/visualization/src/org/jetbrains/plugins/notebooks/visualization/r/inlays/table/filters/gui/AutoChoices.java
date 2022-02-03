/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

/**
 * Enumeration to define the available auto choices modes on a table filter or
 * on each separated filter editor.
 */
public enum AutoChoices {

    /** No auto choices, any choices must be explicitly inserted. */
    DISABLED,

    /** Enumerations and booleans automatically handled. */
    ENUMS,

    /**
     * Choices extracted from the model, it is guaranteed that the choices
     * include all the model's values, and only those.
     */
    ENABLED
}
