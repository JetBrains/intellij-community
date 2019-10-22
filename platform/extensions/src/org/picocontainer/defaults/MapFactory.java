// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.picocontainer.defaults;

import java.util.Map;

/**
 * A simple factory for ordered maps: use JDK1.4's java.util.LinkedHashMap if available,
 * or commons-collection's LinkedMap, or defaults to unordered java.util.HashMap
 *
 * @author Gregory Joseph
 * @version $Revision: $
 */
public class MapFactory {
    private static final String JDK14 = "java.util.LinkedHashMap";
    private static final String COMMONS = "org.apache.commons.collections.map.LinkedMap";
    private static final String NON_ORDERED = "java.util.HashMap";

    private Class clazz;

    public MapFactory() {
        try {
            clazz = Class.forName(JDK14);
        } catch (ClassNotFoundException e) {
            try {
                clazz = Class.forName(COMMONS);
            } catch (ClassNotFoundException e1) {
                try {
                    clazz = Class.forName(NON_ORDERED);
                } catch (ClassNotFoundException e2) {
                    throw new IllegalStateException("What kind of JRE is this ? No " + NON_ORDERED + " class was found.");
                }
            }
        }
    }

    public Map newInstance() {
        try {
            return (Map) clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException("Could not instantiate " + clazz + " : " + e.getMessage());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not instantiate " + clazz + " : " + e.getMessage());
        }
    }
}
