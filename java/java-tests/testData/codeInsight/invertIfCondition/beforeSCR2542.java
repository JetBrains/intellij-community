// "Invert If Condition" "true"
// else on the same line
class TestInvertIf {
    void invertIf(Object object) {
        <caret>if (object == "adf") {
            System.out.println("2");
        } else {
            System.out.println("1");
        } // comment
    }
}