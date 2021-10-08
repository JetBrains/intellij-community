// "Replace 'switch' with 'if'" "true"
class Test {
    void test() {
        enum P {
            s;
        }
        P p = null;
        <caret>switch (p) {
            case s -> {}
            default -> {}
        }
    }
}