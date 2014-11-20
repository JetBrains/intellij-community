interface I {
    void foo(int i);
}
class Test implements I {
    public void foo(int i) {
        bar();
        bar();
    }

    void bar(){}
}