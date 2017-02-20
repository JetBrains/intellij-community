// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    String f(boolean a, boolean b) {
        if (a) {
            if (b)
                System.out.println("When true");
            <caret>else
                return "Otherwise";
        }
        return "Default";
    }
}