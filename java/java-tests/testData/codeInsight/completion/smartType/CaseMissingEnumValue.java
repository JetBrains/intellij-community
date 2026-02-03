class A {
    A(MyEnum e) {
      switch (e) {
        case bar: return;
        case <caret>
      }
    }
}

enum MyEnum { foo, bar }