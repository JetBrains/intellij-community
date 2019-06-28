// "Wrap labeled rule's result expression with code block" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> /*1*/{
                yield Integer.toString(/*2*/n);
            }/*3*/
            default -> "b";
        };
    }
}