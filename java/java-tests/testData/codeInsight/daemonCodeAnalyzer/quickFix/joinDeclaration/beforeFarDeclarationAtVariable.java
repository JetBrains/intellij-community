// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(int x) {
        int <caret>a;
        int b = System.in.read();
        a = x + 1; /*A*/ /*B*/ // C
        return a + b;
    }
}