// "Unwrap 'else' branch" "false"

class T {
    String f(boolean b) {
        if (b)
            return "When true";
        <caret>else
            // Before
            return "Otherwise";
            // After
    }
}