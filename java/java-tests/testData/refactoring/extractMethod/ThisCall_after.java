public class A {
    A(String s) {
    }

    A() {
        this(newMethod());
    }

    private static String newMethod() {
        return "a";
    }
}