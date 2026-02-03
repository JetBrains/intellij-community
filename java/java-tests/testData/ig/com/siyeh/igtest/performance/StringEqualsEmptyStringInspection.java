package com.siyeh.igtest.performance;

import java.io.IOException;

public class StringEqualsEmptyStringInspection {
    public StringEqualsEmptyStringInspection() {
    }

    public void foo() throws IOException {
        "foo".equals("f");
        "foo".equals("");
        if ("foo".equals("")) {
            System.out.println("");
        }
        if (!"foo".equals("")) {
            System.out.println("");
        }
    }
}