// "Create getter for '_i'" "true"
public enum TestEnum {
    ;
    private final int _i;

    private TestEnum(int _i) {
        this._i = _i;
    }

    public int get_i() {
        return _i;
    }
}