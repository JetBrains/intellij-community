class Test<X> {
    private static Test<Short> migrationField;

    private static void m(Short x1, Short x2) {
        migrationField.method(x1, x2);
    }

    void method(X... xes) {

    }
}
