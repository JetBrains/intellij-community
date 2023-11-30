package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
    int test(Object obj, int x) {
        <caret>switch (obj) {
            case Integer y -> {
                Integer z = y;
                return y.byteValue();
            }
            case String r when x > 0 -> {
                return ((String) obj).length();
            }
            case Character c -> {
                return ((BigDecimal) obj).hashCode();
            }
            case null, default -> {
                return -1;
            }
        }
    }
}