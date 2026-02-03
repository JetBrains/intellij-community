public enum EEnum {
    FOO(0), BAR(2);
    private final int value;

    EEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}