class Tester {
    void method(String x, String... y) {
    }

    void method1(String x, String[] y) {
    }

    void caller() {
        String[] thing = {"a", "b"};
        method("", <caret>thing);
        method1("", thing);
    }
}