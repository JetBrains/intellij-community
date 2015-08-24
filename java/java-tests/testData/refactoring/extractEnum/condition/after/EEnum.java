public enum EEnum {
    STATE_STARTED(0), STATE_STOPPED(1);
    private int value;

    public int getValue() {
        return value;
    }

    EEnum(int value) {
        this.value = value;
    }
}