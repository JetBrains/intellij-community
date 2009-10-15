enum MyEnum {
  FOO, BAR
}

@interface Anno {
  MyEnum value();
}


@Anno(MyEnum.F<caret>)
class Foo {
}