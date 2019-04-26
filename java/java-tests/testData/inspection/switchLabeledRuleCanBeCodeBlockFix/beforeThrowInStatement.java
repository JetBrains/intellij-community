// "Wrap labeled rule's statement with code block" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        String s;
        switch (n) {
            <caret>case 1 -> /*1*/ throw /*2*/new RuntimeException(); /*3*/
            default -> s = "b";
        };
        return s;
    }
}