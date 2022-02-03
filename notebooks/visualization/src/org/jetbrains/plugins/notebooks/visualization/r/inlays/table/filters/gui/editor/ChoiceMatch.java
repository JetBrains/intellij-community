/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor;

import java.util.Comparator;

/** Class to find matches in the lists (history / choices). */
class ChoiceMatch {
    // exact is true if the given index corresponds to an string that fully
    // matches the passed string
    boolean exact;
    // the matched content
    Object content;
    // index in the list. Will be -1 if the content is empty or a fullMatch
    // is required and cannot be found
    int index = -1;
    // length of the string that is matched, if exact is false.
    int len;

    /** Returns the number of matching characters between two strings. */
    public static int getMatchingLength(String a, String b, Comparator stringComparator) {
        int max = Math.min(a.length(), b.length());
        for (int i = 0; i < max; i++) {
            char f = a.charAt(i);
            char s = b.charAt(i);
            if ((f != s)
                    && (stringComparator.compare(String.valueOf(f),
                            String.valueOf(s)) != 0)) {
                return i;
            }
        }

        return max;
    }

}
