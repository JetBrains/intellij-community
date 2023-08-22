package com.siyeh.igfixes.performance.trivial_string_concatenation;

class StartComments {
    void m() {
        /* hello2 */
        String s1 = /* hello1 */ /* hello3 */ "<caret>test1" /* hello4 */ +
                       "test2";
    }
}