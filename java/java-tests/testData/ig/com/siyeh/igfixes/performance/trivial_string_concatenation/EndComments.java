package com.siyeh.igfixes.performance.trivial_string_concatenation;

class StartComments {
    void m() {
        String s1 = /* hello1 */ "test1" /* hello2 */ +
          /* hello3 */ "test2" // hello4
         +  /* hello5 */ "<caret>" /* hello6 */ ;
    }
}