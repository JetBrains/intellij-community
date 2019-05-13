@interface Foo {
  MyEnum value();
}
enum MyEnum { x, y }

@Foo(<caret>)