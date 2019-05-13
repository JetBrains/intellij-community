enum Foo {}
enum Bar {}
class Doo {
    void foo(Foo f) {}
    void foo(Bar f) {}

    void bar() {
        foo(valu<caret>)
    }
}