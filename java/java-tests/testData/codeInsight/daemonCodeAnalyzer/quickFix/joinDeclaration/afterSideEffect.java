// "Join declaration and assignment" "true"
class T {
    {
        foo(1);
        int a = foo(2);
    }
    static int foo(int n) {
        System.out.println(n);
        return n + 1;
    }
}