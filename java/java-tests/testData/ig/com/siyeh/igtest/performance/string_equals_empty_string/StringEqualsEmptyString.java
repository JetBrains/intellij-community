package com.siyeh.igtest.performance.string_equals_empty_string;

public class StringEqualsEmptyString {

    void foo(String s) {
        boolean b = s.<warning descr="'equals(\"\")' can be replaced with 'isEmpty()'">equals</warning>("");
        boolean c = "".<warning descr="'equals(\"\")' can be replaced with 'isEmpty()'">equals</warning>(s);
        boolean d = "a".equals("b");
        boolean e = "".equals(evaluate(123));
        boolean f = evaluate(123).<warning descr="'equals(\"\")' can be replaced with 'isEmpty()'">equals</warning>("");
    }
    
    String evaluate(int x) {
        return String.valueOf(x);
    }
}
