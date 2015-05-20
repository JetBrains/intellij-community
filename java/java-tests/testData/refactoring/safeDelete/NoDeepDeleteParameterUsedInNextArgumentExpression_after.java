class Test {
    void foo(String p) {}

    void bar(String s, String p) {
        foo(s.substring(0));
    }
}