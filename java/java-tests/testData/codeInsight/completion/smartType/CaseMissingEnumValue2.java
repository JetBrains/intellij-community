class A {
    A(MyEnum e) {
      switch (e) {
        case <caret>
        case bar: return;
      }
    }
}

enum MyEnum { foo, bar }