
public class Foo {
    public static Foo foo(int i1, int i2, int i3) {
        Foo.foo(1, 2, 3)
                .foo(4, 5, 6)
                .foo(7, 8, 9)
                .foo(x, y, z);
    }
}