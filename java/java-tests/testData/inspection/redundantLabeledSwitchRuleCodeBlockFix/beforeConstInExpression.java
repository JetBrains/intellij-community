// "Unwrap code block of labeled rule" "LIKE_UNUSED_SYMBOL"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> /*1*/ { /*2*/<caret>yield /*3*/"a"/*4*/; /*5*/ } /*6*/
            default -> "b";
        };
    }
}