// "Unwrap 'else' branch" "true"

class T {
    String f(boolean b) {
        if (b)
            return "When true";
            // Before
        return "Otherwise";
        // After
    }
}