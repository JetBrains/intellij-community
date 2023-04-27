import org.checkerframework.checker.tainting.qual.*;

class Simple {

    void test() {
        String s = callAnother();
        sink(<caret>s);
    }

    String callAnother() {
        Another another = new Another();
        return ((another.foo()) + (another.foo()));
    }

    static String bar() {
        return "safe";
    }

    void sink(@Untainted String s) {}
}

class Another {
    String foo() {
        return Simple.bar();
    }
}