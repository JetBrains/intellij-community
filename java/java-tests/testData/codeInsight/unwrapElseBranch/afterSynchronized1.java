// "Unwrap 'else' branch (changes semantics)" "true"

class T {
    void f(Object o, boolean b) {
        synchronized (o) {
            if (b)
                System.out.println("When true");
            throw new RuntimeException("Otherwise");
        }
    }
}