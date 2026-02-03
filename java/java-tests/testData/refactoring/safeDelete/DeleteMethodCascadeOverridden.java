interface I {
    void bar();
}
class Test implements I{
    void f<caret>oo() {
        bar();
    }

    public void bar(){
        baz();
    }

    void baz(){}
}