public enum EEnum {
    FOO(0);
    private final int value;

    EEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}