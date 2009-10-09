public class A {
    final String f;

    public A(String foo, String fi) {
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
        super(foo, fi);
        this.foo = foo;
    }


}
