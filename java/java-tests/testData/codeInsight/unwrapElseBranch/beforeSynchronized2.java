// "Unwrap 'else' branch (changes semantics)" "false"

class T {
    void f(Object o, boolean b) {
        synchronized (o) {
            if (b)
                System.out.println("When true");
            <caret>else {
                throw new RuntimeException("Otherwise");
            }
        }
        System.out.println("Done");
    }
}