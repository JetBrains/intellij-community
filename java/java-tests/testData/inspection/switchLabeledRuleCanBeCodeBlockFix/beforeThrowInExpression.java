// "Wrap labeled rule's statement with code block" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        return switch (n) {
            <caret>case 1 -> /*1*/ throw new RuntimeException(); /*2*/
            default -> "b";
        };
    }
}