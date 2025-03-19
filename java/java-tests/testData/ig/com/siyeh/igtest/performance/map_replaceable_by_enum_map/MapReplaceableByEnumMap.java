package com.siyeh.igtest.performance.map_replaceable_by_enum_map;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.SortedMap;

public class MapReplaceableByEnumMap {

    public static void main(String[] args) {
        final HashMap<MyEnum, Object> myEnums = new <warning descr="'HashMap<MyEnum, Object>' can be replaced with 'EnumMap'">HashMap<MyEnum, Object></warning>();
    }

    enum MyEnum{
        foo, bar, baz;
        Map<MyEnum, Object> enums = new HashMap();
        // enum map here throws exception at runtime -> don't suggest it
    }

    void foo() {
        final Map<MyEnum, Object> map = new TreeMap(Collections.reverseOrder());
        SortedMap<MyEnum, String> schmap = new TreeMap<>();
    }
}
