// "Create getter for '_i'" "true-preview"
public enum TestEnum {
    ;
    private final int _<caret>i;

    private TestEnum(int _i) {
        this._i = _i;
    }
}