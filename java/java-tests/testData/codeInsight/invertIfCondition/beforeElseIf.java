// "Invert 'if' condition" "true"
class A {
    public void foo(boolean c, boolean c2) {
        if (c) a();
        else if (!c<caret>2) b();
    }
}