// "Invert 'if' condition" "true"
class Main {
    Object foo(boolean b1, boolean b2) {
        if (b1) {
            return null;
        } else {
            if (b2) {
                return null;
            }
            return "a";
        }

    }
}