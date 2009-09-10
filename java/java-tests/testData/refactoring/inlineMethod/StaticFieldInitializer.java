class A{
    static int field = foo();

    static int <caret>foo(){
        doSomething();
        return 1;
    }
}
