// "Unwrap 'else' branch" "false"

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