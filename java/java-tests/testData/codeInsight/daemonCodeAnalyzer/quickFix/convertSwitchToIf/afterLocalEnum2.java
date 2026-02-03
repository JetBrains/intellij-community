import java.util.Objects;

// "Replace 'switch' with 'if'" "true-preview"
class Test {
    void test() {
        enum P {
            s, l;
        }
        P p = null;
        if (Objects.requireNonNull(p) == P.s || p == P.l) {
        }
    }
}