interface Foo_Intf {}
class FooClass {}

class Goo extends Foo<caret> {
    int boo() {}
    int doo() {}
    int foo() {}
}
