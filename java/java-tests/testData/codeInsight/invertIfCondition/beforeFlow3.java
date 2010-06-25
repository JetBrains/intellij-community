// "Invert If Condition" "true"
class A {
    public void foo(boolean c) {
        <caret>if (!c) return;
        foo();
    }
}