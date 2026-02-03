interface I {
    void bar();
}
class Test implements I{

    public void bar(){
        baz();
    }

    void baz(){}
}