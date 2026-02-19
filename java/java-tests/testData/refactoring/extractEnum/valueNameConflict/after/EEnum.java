public enum EEnum {
    FOO("foo");
    public int value = 0;
    private final String value1;

    EEnum(String value) {
        value1 = value;
    }

    public String getValue() {
        return value1;
    }
}