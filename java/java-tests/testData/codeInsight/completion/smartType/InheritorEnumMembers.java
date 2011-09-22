interface Foo {
}

enum FooEnum implements Foo {
    ONE, TWO;
}

class Doo {
    void doSomething(Foo f) {
    }

    void doSomethingElse() {
        doSomething(O<caret>);
    }

}

