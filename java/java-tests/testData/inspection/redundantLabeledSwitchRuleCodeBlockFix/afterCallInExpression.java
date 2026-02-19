// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> Integer.toString(n);
            default -> "b";
        };
    }
}