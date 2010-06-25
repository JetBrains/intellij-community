// "Invert If Condition" "true"
class A {
    public void foo() {
        while (true) {
            if (c) {
                <caret>if (d) {
                    a();
                    break;
                }
            }
            else if (e) {
            }
        }
    }
}