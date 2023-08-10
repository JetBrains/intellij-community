// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(final int x, int n) {
        return switch (x) {
            case 0 -> {
                <caret>yield /*1*/ switch (n) { // 2
                    case 0 -> 0;
                    default -> 1;
                }/*3*/; //4
            }
            default -> 2;
        };
    }
}