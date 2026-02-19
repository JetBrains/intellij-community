import org.checkerframework.checker.tainting.qual.*;

class Simple {

    void test() {
        String s = callAnother();
        sink(s);
    }

    @Untainted
    String callAnother() {
        Another another = new Another();
        return ((another.foo()) + (another.foo()));
    }

    static String bar(x) {
        return x;
    }

    void sink(@Untainted String s) {}
}

class Another {
  String x;

    @Untainted
    String foo() {
        return Simple.bar(x);
    }
}