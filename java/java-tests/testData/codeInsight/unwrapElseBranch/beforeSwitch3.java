// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(boolean b, int n) {
        switch (n) {
            case 1:
                if (b)
                    System.out.println("When true");
                <caret>else {
                    // Before
                    System.out.println("Otherwise");
                    // After
                    return;
                }
            case 2:
        }
        System.out.println("Done");
    }
}