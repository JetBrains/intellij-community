/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor;

/**
 * Interface to escape properly choices, so that the IParser handles them
 * properly. In special, HTML content must be always removed from the choices.
 * <br>
 * Starting on version 4.3, the parser is also able to handle html content;
 * in this case, the parser accepts simple text, but the created filter can
 * be applied to Html content 
 */
interface IChoicesParser {

    /**
     * Escapes a given expression, such that, when parsed, the parser will make
     * no character/operator substitutions.
     */
    String escapeChoice(String s);
}
