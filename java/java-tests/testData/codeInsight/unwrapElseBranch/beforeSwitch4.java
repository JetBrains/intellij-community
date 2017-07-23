// "Unwrap 'else' branch (changes semantics)" "false"

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
        }
        System.out.println("Done");
    }
}