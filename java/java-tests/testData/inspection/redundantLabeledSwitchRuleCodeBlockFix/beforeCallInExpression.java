// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            <caret>case 1 -> { break Integer.toString(n); }
            default -> "b";
        };
    }
}