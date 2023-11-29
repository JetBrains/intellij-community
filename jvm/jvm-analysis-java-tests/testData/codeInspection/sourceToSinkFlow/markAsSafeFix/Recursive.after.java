import org.checkerframework.checker.tainting.qual.*;

class Simple {

    void test() {
        String s = "";
        String s1 = id(id(s));
        s += s1;
        sink(id(s));
    }

    @Untainted
    String id(String s) {
        return id(s);
    }

    void sink(@Untainted String s) {}
}