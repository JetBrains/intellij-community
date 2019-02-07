// "Unwrap code block of labeled rule" "LIKE_UNUSED_SYMBOL"
class C {
    int foo(final int x, int n) {
        return switch (x) {
            case 0 -> /*1*/ /*3*/ //4
                    switch (n) { // 2
                        case 0 -> 0;
                        default -> 1;
                    };
            default -> 2;
        };
    }
}