enum MyEnum {
  FOO, BAR
}

class Foo {
  {
    MyEnum me = MyEnum.FOO;<caret>
  }
}