// "Make 'Bar' implement 'java.lang.Runnable'" "true"

abstract class Foo {
    abstract Runnable foo();
}

class FooBar extends Foo {
    @Override
    Bar foo() {
        return null;
    }

    static class Bar implements Runnable {
        public void run() {
            <caret>
        }
    }
}

