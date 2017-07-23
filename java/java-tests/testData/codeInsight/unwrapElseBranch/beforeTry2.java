// "Unwrap 'else' branch (changes semantics)" "false"

class T {
    void f(boolean b) {
        try {
            if (b)
                System.out.println("When true");
            <caret>else {
                throw new RuntimeException("Otherwise");
            }
        } finally {
        }
        System.out.println("Done");
    }
}