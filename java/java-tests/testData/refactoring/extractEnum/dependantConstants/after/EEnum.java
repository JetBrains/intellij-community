public enum EEnum {
    FOO("foo"), BAR(FOO.getValue());
    private String value;

    public String getValue() {
        return value;
    }

    EEnum(String value) {
        this.value = value;
    }
}