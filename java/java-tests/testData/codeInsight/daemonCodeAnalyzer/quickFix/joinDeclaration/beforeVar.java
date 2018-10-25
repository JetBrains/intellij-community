// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo() {
        var <caret>a = 0;
        a = 1;
        return a;
    }
}