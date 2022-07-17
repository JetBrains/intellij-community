import foo.TestNotNull;

class C {
    private @TestNotNull String s;

    C(@TestNotNull String s) {
        this.s = s;
    }
}