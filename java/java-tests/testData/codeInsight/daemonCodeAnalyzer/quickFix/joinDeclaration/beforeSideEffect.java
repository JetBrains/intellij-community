// "Join declaration and assignment" "true"
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