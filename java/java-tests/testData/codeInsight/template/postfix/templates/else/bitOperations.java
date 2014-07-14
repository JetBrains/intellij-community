public class Foo {
    void m(boolean x, boolean y, boolean z) {
        x & y && z || x ^ y.else<caret>
        foo();
    }
}