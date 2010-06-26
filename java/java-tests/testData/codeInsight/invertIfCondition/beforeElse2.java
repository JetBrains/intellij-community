// "Invert If Condition" "true"
class A {
    public void foo() {
        <caret>if (c) {}
        else b();
    }
}