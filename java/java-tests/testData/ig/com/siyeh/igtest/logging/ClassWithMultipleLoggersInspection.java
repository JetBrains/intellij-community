package com.siyeh.igtest.logging;

import java.util.logging.Logger;

public class ClassWithMultipleLoggersInspection {
    private Logger foo = Logger.getLogger("foo");
    private Logger bar = Logger.getLogger("bar");
    private ClassWithMultipleLoggersInspection() {
    }

    public static void main(String[] args) {

    }
}
