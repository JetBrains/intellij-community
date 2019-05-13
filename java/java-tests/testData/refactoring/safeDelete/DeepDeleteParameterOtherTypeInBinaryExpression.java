class Test {
    void foo(String s) {
        bar(s.length());
        bar(s.length() + 1);
    }

    void bar(int <caret>i){}
}