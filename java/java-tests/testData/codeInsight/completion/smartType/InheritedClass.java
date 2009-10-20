class Foo {}
class Bar extends Foo {

void test() {
  method(new B<caret>)
}

void method(Foo f) {}

}