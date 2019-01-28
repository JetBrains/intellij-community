// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo() {
        final int <caret>a;
        a = 1;
        return a;
    }
}