package p;

import static p.EnumA.values;
import static p.A.a;
import static p.Foo.*;

class U {
  void foo() {
    EnumA[] aValues = values();
    EnumB[] bValues = EnumB.values();
  }
  
  void bar() {
    a();
    B.a();
  }

  void aaaa() {
    Foo.<String>foo();
  }
}