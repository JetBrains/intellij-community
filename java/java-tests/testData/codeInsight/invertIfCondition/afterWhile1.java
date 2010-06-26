// "Invert If Condition" "true"
class A {
    public void foo() {
        while (true) {
            <caret>if (!c) {
                continue;
            }
            a();
        }
    }
}