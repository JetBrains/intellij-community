// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class T {
    {
        int a =<caret> foo(1);
        a = foo(2);
    }
    static int foo(int n) {
        System.out.println(n);
        return n + 1;
    }
}