// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        if (a)
            <caret>if (!b)
                return true;
        return false;
    }
}