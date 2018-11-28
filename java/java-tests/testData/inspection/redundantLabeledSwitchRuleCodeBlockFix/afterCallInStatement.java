// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        String s;
        switch (n) {
            <caret>case 1 -> System.out.println(n);
            default -> System.out.println();
        };
    }
}