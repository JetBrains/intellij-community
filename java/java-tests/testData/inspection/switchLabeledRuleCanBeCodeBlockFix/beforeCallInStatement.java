// "Wrap labeled rule's statement with code block" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        String s;
        switch (n) {
            <caret>case 1 -> /*1*/s = Integer.toString(/*2*/n);/*3*/
            default -> s = "b";
        };
        return s;
    }
}