public class A {
    final String f;

    public A() {
        f = "";
    }
}

class B extends A {
    final String foo;

    B(String fi, String foo) {
        super();
        this.foo = foo;
    }

    B(String foo) {
        super();
        this.foo = foo;
    }
}
