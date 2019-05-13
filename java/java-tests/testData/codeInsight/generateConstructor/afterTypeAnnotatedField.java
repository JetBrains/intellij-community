import foo.TestNotNull;

class C {
    private @TestNotNull String s;

    public C(@TestNotNull String s) {
        this.s = s;
    }
}