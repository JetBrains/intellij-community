// "Unwrap 'else' branch" "false"

class T {
    String f(boolean a, boolean b) {
        if (b) {
            return "When true";
        } else if (a) {
            return "a";
        } <caret>else {
            return "Otherwise";
        }
        return "Default";
    }
}