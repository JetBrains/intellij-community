// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        if (a) {
            if (b) {
            }
            else {
                return true; // comment
            }
        }
        int x = 1;
        return false;
    }
}