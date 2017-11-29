// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        for (int i = 1; i < 10; i++) {
            if (b) {
                continue;
            }
            return true;
        }
        return false;
    }
}