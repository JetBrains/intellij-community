public enum EEnum {
    FOO("foo");
    public int value = 0;
    private String value1;

    public String getValue() {
        return value1;
    }

    EEnum(String value) {
        value1 = value;
    }
}