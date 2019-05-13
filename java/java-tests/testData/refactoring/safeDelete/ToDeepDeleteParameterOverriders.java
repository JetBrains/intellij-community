interface I {
    void foo(int i);
}
class Test implements I {
    public void foo(int i) {
        bar(i);
        bar(i);
    }

    void bar(int <caret>i){}
}