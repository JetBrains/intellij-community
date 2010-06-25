// "Invert If Condition" "true"
class A {
    public void foo() {
        if (<caret>!c) {
            b();
        }
        else {
            a();
        }
    }
}