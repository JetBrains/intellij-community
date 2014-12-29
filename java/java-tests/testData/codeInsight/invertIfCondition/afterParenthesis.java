// "Invert 'if' condition" "true"
class A {
    public boolean foo(boolean a, boolean b, boolean c, boolean d) {

        if ((a || b) && !c && !d) {
            return true;
        }
        return false;
    }
}