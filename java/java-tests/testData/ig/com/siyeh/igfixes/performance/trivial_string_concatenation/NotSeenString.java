package com.siyeh.igfixes.performance.trivial_string_concatenation;

class NotSeenString {
    void m(Integer i) {
        String s1 = 1 + i /*hi*/ + "<caret>";
        String s1_expr = 1 + (i + /*hi*/ 2) + "";
        String s2 = "" + 1 + i + null;
        String s2_expr = "" + (3 + /*hi*/ 2) + i + null;
        String s2_str = "" + "text" + i + null;
        String s3 = i + 1 + "" + null;
        String s3_expr = i + 1 + "" + (3 + /*hi*/ 2);
        String s3_str = i + 1 + "" + "text";
        String s4 = new Object() + "" + 1 + i + null;
        String s4_expr = new Object() + "" + (3 + /*hi*/ 2) + null + i;
        String s4_str = new Object() + "" + "text" + null + i;
        String s5 = "" + null + i + null;
        String s6 = "" + new Object() + i + null;
        String s7 = "" + new Object() + "text" + i + null;
        String s8 = "" + "text" + i + null;

        String pair1 = i + "";
        String pair2 = "" + i;
        String pair3 = "" + null;
        String pair4 = null + "";

        String triple1 = "" + "" + i;
        String triple2 = i + "" + i;
        String triple3 = "" + i + i;
    }
}