// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

    void test() {
        String s = callAnother();
        sink(s);
    }

    @Untainted String callAnother() {
        Another another = new Another();
        return ((another.foo()) + (another.foo()));
    }

    static @Untainted String bar() {
        return "safe";
    }

    void sink(@Untainted String s) {}
}

class Another {
    @Untainted String foo() {
        return Simple.bar();
    }
}