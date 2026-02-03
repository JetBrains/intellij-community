package pack1;

public class C {
    public static int ourField = 10;

    private static void bar() {
        foo();
        ourField = 11;
    }

    public static void foo() {
        bar();
    }
}