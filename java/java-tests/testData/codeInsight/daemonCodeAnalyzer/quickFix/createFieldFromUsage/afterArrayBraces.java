// "Create field 'fooMoo'" "true-preview"
class Barr {
    private final String s;
    private final Foo[] fooMoo;

    enum Foo {}

    Barr(String s, Foo... foos) {
        this.s = s;
        this.fooMoo = foos;
    }
}