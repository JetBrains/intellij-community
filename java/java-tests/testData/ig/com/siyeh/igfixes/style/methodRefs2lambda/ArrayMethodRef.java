public class Foo {
    static void foo() {
        Cln j = i<caret>nt[]::clone;
    }

    interface Cln {
        Object _(int[] p);
    }
}