package com.siyeh.igfixes.performance.trivial_string_concatenation;

class StartComments {
    void m() {
        String s1 = /* hello1 */ "<caret>" /* hello2 */ +
          /* hello3 */ "test1" /* hello4 */ +
                       "test2";
    }
}