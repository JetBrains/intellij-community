// "Make 'Bar' implement 'java.lang.Runnable'" "true-preview"

abstract class Foo {
    abstract Runnable foo();
}

class FooBar extends Foo {
    @Override
    Bar foo() {
        return null;
    }

    static class Bar implements Runnable {
        @Override
        public void run() {
            <caret>
        }
    }
}

