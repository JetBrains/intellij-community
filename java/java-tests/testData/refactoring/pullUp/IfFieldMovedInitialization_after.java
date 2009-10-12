public class A {
    final String f;

    public A(String fi, String foo) {
        if (fi == foo) {
            f = foo;
        } else {
            f = "";
        }
    }
}

class B extends A {
    final String foo;

    B(String fi, String foo) {
        super(fi, foo);
        this.foo = foo;
    }


}
