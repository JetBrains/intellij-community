// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(boolean a, boolean b) {
        if (a) {
            if (b)
                System.out.println("When true");
            <caret>else {
                System.out.println("Otherwise");
                return;
            }
        }
    }
}