/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import java.awt.*;

/**
 * Interface to customize the rendering of choices in the {@link IFilterEditor}.
 */
public interface ChoiceRenderer {

    /**
     * Returns the component used to represent the choice (normally, an element
     * from the associated table).<br>
     * The value can be as well {@link CustomChoice} instances; to use the
     * default rendering in this case, the method should return null.
     */
    Component getRendererComponent(IFilterEditor editor, Object value, boolean isSelected);
}
