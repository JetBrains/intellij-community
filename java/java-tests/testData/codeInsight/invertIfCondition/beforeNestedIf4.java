// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        for (int i = 1; i < 10; i++)
            <caret>if (!b)
            return true;
        return false;
    }
}