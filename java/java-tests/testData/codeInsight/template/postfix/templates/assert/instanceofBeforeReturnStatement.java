package templates;

public class Foo {
    boolean m(Object o) {
        if (o instanceof T1) return true;
        o instanceof T2.assert<caret>

        return false;
    }
}