// "Unwrap 'else' branch" "true"

class T {
    String f(boolean a, boolean b) {
        if (a)
            if (b) {
                return "When true";
            } <caret>else {
                return "Otherwise";
            }
        return "Default";
    }
}