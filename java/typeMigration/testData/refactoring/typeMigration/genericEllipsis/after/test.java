class Test<X> {
    private static Test<Short> migrationField;

    private static void m() {
        migrationField.method();
    }

    void method(X... xes) {

    }
}
