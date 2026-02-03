abstract class Bazz {
    public abstract void foo();
}

abstract class Foo extends Bazz {
    @Override
    public void foo() {
    }
}

class Bar extends Foo {
}

