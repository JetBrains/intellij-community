class Foo {
  public void get(MyEnum e) {
    switch (e) {
      case <caret>
    }
  }
}

enum MyEnum {
  FOO, BAR, GOO
}
