public enum EEnum {
    STATE_STARTED(0), STATE_STOPPED(1);
    private final int value;

    EEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}