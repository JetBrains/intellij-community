class A {
    A(MyEnum e) {
      switch (e) {
        case foo:<caret>
        case bar: return;
      }
    }
}

enum MyEnum { foo, bar }