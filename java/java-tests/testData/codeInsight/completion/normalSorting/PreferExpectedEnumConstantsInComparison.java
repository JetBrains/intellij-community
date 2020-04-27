class Foo {
  boolean foo(MyEnum e) {
    return e == <caret>
  }
}

enum MyEnum { const1, const2 }