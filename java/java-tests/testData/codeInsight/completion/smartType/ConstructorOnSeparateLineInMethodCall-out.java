class Main {
    private static void method(Integer param) {
    }

    public static void main(String[] args) {
        method(
                new Integer(<caret>)
        );
    }
}