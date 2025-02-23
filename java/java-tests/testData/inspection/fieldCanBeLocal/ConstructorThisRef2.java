class ClassName {
    private static String <warning descr="Field can be converted to a local variable">SOME_CONSTANT</warning> = "XXX";

    public ClassName() {
        this(SOME_CONSTANT);
    }

    public ClassName(String somethingElse) {
    }
}