interface FooIntf {}
class FooClass {}

class Goo implements Foo<caret> {
    int boo() {}
    int doo() {}
    int foo() {}
}
