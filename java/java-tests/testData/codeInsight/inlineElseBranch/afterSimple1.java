// "Inline 'else' branch" "true"

class T {
    void f(boolean b) {
        if (b)
            System.out.println("When true");
            // Before
        System.out.println("Otherwise");
        // After
    }
}