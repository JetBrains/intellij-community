// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> /*1*/ /*2*/ /*3*/ /*4*/ /*5*/ "a"; /*6*/
            default -> "b";
        };
    }
}