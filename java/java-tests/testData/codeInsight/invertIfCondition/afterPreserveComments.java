// "Invert 'if' condition" "true"
class A {
    public void foo() {
        if (!c) {
            return;
        } //comments to restore
        a();
    }
}