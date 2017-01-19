// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(boolean b) {
        if (b) {
            System.out.println("When true");
        } <caret>else {
            System.out.println("Otherwise");
        }
    }
}