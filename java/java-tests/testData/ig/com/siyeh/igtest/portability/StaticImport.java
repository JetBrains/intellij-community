package com.siyeh.igtest.portability;

import static java.lang.Math.abs;
import static java.util.AbstractMap.*;

public class StaticImport {

    public static void main(String[] args) {
        abs(3.0);
        Entry entry;
    }
}
