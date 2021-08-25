// "Replace 'switch' with 'if'" "true"
class Test {
    void test() {
        enum P {
            s;
        }
        P p = null;
        if (p == P.s) {
        }
    }
}