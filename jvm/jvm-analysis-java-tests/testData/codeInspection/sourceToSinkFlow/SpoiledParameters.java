import org.checkerframework.checker.tainting.qual.Untainted;

class SpoiledParameters {

    String spoiltField;

    void test() {
        sink(<warning descr="Unknown string is used as safe parameter">spoilMethod("")</warning>);
        sink(next(""));
    }

    private String spoilMethod(String willSpoil) {
        willSpoil = spoiltField;
        return willSpoil;
    }

    private String next(String next) {
        return next;
    }

    void sink(@Untainted String s1) {
    }
}