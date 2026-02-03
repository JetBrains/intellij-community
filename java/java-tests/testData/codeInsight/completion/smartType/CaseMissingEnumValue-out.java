class A {
    A(MyEnum e) {
      switch (e) {
        case bar: return;
        case foo:<caret>
      }
    }
}

enum MyEnum { foo, bar }