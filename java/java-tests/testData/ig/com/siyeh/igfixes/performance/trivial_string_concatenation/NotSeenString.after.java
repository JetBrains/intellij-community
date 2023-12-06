package com.siyeh.igfixes.performance.trivial_string_concatenation;

class NotSeenString {
    void m(Integer i) {
        String s1 = String.valueOf(1<caret> + i);
        String s1_expr = String.valueOf(1 + (i + /*hi*/ 2));
        String s2 = String.valueOf(1) + i + null;
        String s2_expr = String.valueOf(3 + /*hi*/ 2) + i + null;
        String s2_str = "text" + i + null;
        String s3 = i + 1 + String.valueOf((Object) null);
        String s3_expr = i + 1 + String.valueOf(3 + /*hi*/ 2);
        String s3_str = i + 1 + "text";
        String s4 = new Object() + String.valueOf(1) + i + null;
        String s4_expr = new Object() + String.valueOf(3 + /*hi*/ 2) + null + i;
        String s4_str = new Object() + "text" + null + i;
        String s5 = String.valueOf((Object) null) + i + null;
        String s6 = String.valueOf(new Object()) + i + null;
        String s7 = new Object() + "text" + i + null;
        String s8 = "text" + i + null;

        String pair1 = String.valueOf(i);
        String pair2 = String.valueOf(i);
        String pair3 = String.valueOf((Object) null);
        String pair4 = String.valueOf((Object) null);

        String triple1 = String.valueOf(i);
        String triple2 = i + String.valueOf(i);
        String triple3 = String.valueOf(i) + i;
    }
}