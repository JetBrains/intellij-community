// "Make 'Bar' implement 'java.lang.Runnable'" "true-preview"

abstract class Foo {
    abstract Runnable foo();
}

class FooBar extends Foo {
    @Override
    B<caret>ar foo() {
        return null;
    }

    static class Bar {}
}

