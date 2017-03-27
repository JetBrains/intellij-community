// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(boolean b, int n) {
        switch (n) {
            case 1:
                if (b)
                    System.out.println("When true");
                    // Before
                System.out.println("Otherwise");
                // After
            case 2:
        }
    }
}