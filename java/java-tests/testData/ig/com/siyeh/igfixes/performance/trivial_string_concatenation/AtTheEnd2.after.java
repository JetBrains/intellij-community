package com.siyeh.igfixes.performance.trivial_string_concatenation;

class AtTheEnd {

    void foo(Object o) {
        String s = "asdf" + 1 + o;
    }
}