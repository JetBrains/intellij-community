class Test {
    void foo(String <caret>s, String p) {}

    void bar(String s, String p) {
        foo(s, s.substring(0));
    }
}