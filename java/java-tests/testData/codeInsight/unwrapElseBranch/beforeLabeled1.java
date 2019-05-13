// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(boolean a, boolean b) {
        if (a) {
            Label:
            {
                if (b)
                    System.out.println("When true");
                <caret>else {
                    throw new RuntimeException("Otherwise");
                }
            }
        }
        System.out.println("Done");
    }
}