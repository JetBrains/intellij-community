public class Test {
    static void staticContext() {
        extracted();
    }

    void nonStaticContext() {
        extracted();
    }

    private static void extracted() {
        System.out.println("hi");
    }
}