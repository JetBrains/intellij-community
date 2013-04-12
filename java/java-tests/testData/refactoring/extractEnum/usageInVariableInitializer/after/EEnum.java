public enum EEnum {
    FOO(0);
    private int value;

    public int getValue() {
        return value;
    }

    EEnum(int value) {
        this.value = value;
    }
}