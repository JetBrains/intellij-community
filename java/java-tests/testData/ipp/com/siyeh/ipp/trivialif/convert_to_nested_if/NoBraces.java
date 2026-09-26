package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  
    boolean m(boolean a, boolean b, boolean c) {
        if (a) return <caret>b || c;//c1
        return false;//c2
    }
}
