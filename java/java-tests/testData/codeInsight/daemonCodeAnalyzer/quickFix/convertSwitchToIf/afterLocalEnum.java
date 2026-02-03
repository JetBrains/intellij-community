import java.util.Objects;

// "Replace 'switch' with 'if'" "true-preview"
class Test {
    void test() {
        enum P {
            s;
        }
        P p = null;
        if (Objects.requireNonNull(p) == P.s) {
        }
    }
}