// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(int x) {
        int b = System.in.read();
        int a = x + 1; /*A*/ /*B*/ // C
        return a + b;
    }
}