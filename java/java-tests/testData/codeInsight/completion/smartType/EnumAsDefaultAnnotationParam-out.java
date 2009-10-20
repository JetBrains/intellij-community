enum MyEnum {
  FOO, BAR
}

@interface Anno {
  MyEnum value();
}


@Anno(MyEnum.FOO<caret>)
class Foo {
}