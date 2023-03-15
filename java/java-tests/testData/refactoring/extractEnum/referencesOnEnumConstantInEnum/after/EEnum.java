public enum EEnum {
    FOO("foo");
    private final String value;

    EEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    void foo() {
        System.out.println(FOO.getValue());
    }
}