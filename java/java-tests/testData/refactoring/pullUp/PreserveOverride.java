abstract class Bazz {
    public abstract void foo();
}

abstract class Foo extends Bazz {}

class Bar extends Foo {
    @Override
    public void f<caret>oo() {
    }
}

