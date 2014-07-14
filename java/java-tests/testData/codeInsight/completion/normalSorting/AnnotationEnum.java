public class Foo {
  @MyAnno(value = <caret>)
}

@interface MyAnno {
  MyEnum[] value();
}

enum MyEnum { FOO, BAR }