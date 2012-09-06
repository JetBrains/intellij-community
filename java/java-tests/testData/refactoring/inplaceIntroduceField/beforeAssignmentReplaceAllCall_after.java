class Test {
    private final Foo foo;
    private Foo myFoo;

    Test() {
        foo = new Foo();
        myFoo = foo;
    }
}

class Foo{}