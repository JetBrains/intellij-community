// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        if (a) {
            if (b) {
                return false;
            }
            return true;
        }
        return false;
    }
}