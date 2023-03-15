public enum EEnum {
    FOO("foo");
    private final String value;

    EEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}