// "Create field 'fooMoo'" "true-preview"
class Barr {
    private final String s;

    enum Foo {}

    Barr(String s, Foo... foos) {
        this.s = s;
        this.foo<caret>Moo = foos;
    }
}