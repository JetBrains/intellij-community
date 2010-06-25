// "Invert If Condition" "true"
class A {
    public void foo() {
        <caret>if (c == d) {
            b();
        }
        else {
            a();
        }
    }
}