// "Invert 'if' condition" "true"
class A {
    public void foo(boolean c) {
        <caret>if (c) {
            foo();
        }
        else {
            return;
        }
    }
}