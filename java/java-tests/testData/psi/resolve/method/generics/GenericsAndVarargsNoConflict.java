class Test {

    public static void main(String[] args) {
       <caret>method("a", "b", "c");
    }

    public static <T> T method(T... a) {
        return null;
    }

    public static void method(String... a) {}
}