// "Invert If Condition" "true"
class A {
    public void foo() {
        for (int i = 0;;i++) {
            <caret>if (!c) {
                continue;
            }
            a();
        }
    }
}