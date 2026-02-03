package org.example;

public class A {
    void f() {
        String x = true ? ("first ")
                          + "prefix"
                          + ((new StringBuilder()
                              .append("Hello"))
                             .toString()) : ("alternative"
                                             + "prefix")
                                            + ((new StringBuilder()
                                                .append("World"))
                                               .toString());
    }
}