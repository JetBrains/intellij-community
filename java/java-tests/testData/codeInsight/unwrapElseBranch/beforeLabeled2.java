// "Unwrap 'else' branch (changes semantics)" "false"

class T {
    void f(boolean b) {
        Label:
        {
            if (b)
                System.out.println("When true");
            <caret>else {
                throw new RuntimeException("Otherwise");
            }
        }
        System.out.println("Done");
    }
}