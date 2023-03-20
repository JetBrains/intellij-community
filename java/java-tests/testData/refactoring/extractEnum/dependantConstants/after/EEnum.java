public enum EEnum {
    FOO("foo"), BAR(FOO.getValue());
    private final String value;

    EEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}