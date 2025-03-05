/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.element;

import java.util.Comparator;

/**
 * Comparator to sort elements by it's name.
 */
public class ElementComparator implements Comparator<Element> {

    private final int sort;

    /**
     * Construts a comparator that sorts by element name.
     *
     * @param sort   the sorting order (0 = none, 1 = asc, 2 = desc)
     */
    public ElementComparator(int sort) {
        this.sort = sort;
    }

    @Override
    public int compare(Element e1, Element e2) {
        if (sort == 0) {
            return 0;
        }

        String name1 = getElementNameNoLeadingUnderscore(e1);
        String name2 = getElementNameNoLeadingUnderscore(e2);

        int res = name1.compareToIgnoreCase(name2);
        if (sort == 2) {
            res = -1 * res; // flip result if desc
        }

        return res;
    }

    /**
     * Get's the element name without any leading underscores.
     * <p/>
     * Examples: "_age" => "age", "birthday" => "birthday", "year_" => "year_"
     *
     * @param e  the element
     * @return   the name without _ in the start of the name.
     */
    private static String getElementNameNoLeadingUnderscore(Element e) {
        String name = e.getName();
        if (name.startsWith("_")) {
            return name.substring(1);
        } else {
            return name;
        }
    }

}
