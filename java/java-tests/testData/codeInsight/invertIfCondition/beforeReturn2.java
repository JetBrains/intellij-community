// "Invert 'if' condition" "true"
class A {
    public void foo() {
        <caret>if (c) {
            a();
            return "";
        }
        return null;
    }
}