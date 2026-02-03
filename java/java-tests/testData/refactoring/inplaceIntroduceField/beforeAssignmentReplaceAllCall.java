class Test {
    private Foo myFoo;

    Test() {
        new Fo<caret>o()
        myFoo = new Foo();
    }
}

class Foo{}