class Test<X> {
    private static Test<Integer> migrationField;

    private static void m() {
        migrationField.method();
    }

    void method(X... xes) {

    }
}
