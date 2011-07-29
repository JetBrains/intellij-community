class Test {
    private Foo myFoo;
    private final Foo foo;

    Test() {
        foo = new Foo();
        myFoo = foo;
    }
}

class Foo{}