public enum EEnum {
    FOO("foo");
    private String value;

    public String getValue() {
        return value;
    }

    EEnum(String value) {
        this.value = value;
    }
}