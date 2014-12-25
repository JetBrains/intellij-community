import java.util.function.Consumer;

class Test {
    public static void main(String[] args) {
        Consumer<String> ref = Test::method;
        System.out.println(ref);
    }

    static void method(String arg, Object... args) {
        System.out.println(arg);
        System.out.println(args);
    }
    static void method(Object... args) {
        System.out.println(args);
    }
}