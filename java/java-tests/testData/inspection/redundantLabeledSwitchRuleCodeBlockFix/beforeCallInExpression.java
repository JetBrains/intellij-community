// "Unwrap code block of labeled rule" "LIKE_UNUSED_SYMBOL"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> { break<caret> Integer.toString(n); }
            default -> "b";
        };
    }
}