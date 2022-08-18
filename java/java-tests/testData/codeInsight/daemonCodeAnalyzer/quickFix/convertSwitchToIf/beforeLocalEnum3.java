// "Replace 'switch' with 'if'" "true-preview"
class Test {
    void test() {
        enum P {
            s, l;
        }
        P p = null;
        <caret>switch (p) {
            case s, null -> {}
            default -> {}
        }
    }
}