/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.IFilterEditor;

import javax.swing.*;
import java.text.ParseException;

/**
 * Interface defining the requirements on text parsing for filter expressions.
 * <br>
 * Starting on version 4.3, the parser is also able to handle html content;
 * in this case, the parser accepts simple text, but the created filter can
 * be applied to Html content 
 */
public interface IParser {

    /**
     * Parses the text, returning a filter that can be applied to the table.
     *
     * @param  expression  the text to parse
     */
    RowFilter parseText(String expression) throws ParseException;

    /**
     * Parses the text, considered to be a part of the whole text to enter.<br>
     *
     * <p>The behaviour of this method is implementation specific; the default
     * implementation considers the expression to be the beginning of the
     * expected final string</p>
     *
     * <p>This method is invoked when the user inputs text on a filter editor,
     * if instant parsing is enabled, and if the text entered so far does not
     * match any table's row value for the associated column.</p>
     *
     * <p>Alternative implementations that would consider matching the provided
     * expression to any substring ('contain' meaning), should set the
     * autoCompletion flag in the {@link IFilterEditor}to false</p>
     *
     * @param   expression  the text to parse
     *
     * @return  the filter plus the real expression used to create the filter
     */
    InstantFilter parseInstantText(String expression) throws ParseException;

    /**
     * Escapes a given expression, such that, when parsed, the parser will make
     * no character/operator substitutions.
     */
    String escape(String s);

    /**
     * Removes any Html content from the passed string, converting special
     * Html characters to Java characters.
     */
    String stripHtml(String s);

    /** Helper class used on {@link IParser#parseInstantText(String)}. */
    class InstantFilter {
        public RowFilter filter;
        public String    expression;
    }

}
