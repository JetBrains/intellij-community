abstract class Base {
    public static @interface IfNotParsed {}

    static class X {}
}

class Derived extends Base {

}
class B {
    void foo(Derived b) {
        b.<caret>
    }
}