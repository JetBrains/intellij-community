class Foo {}
class Bar extends Foo {

void test() {
  method(new Bar());<caret>
}

void method(Foo f) {}

}