package b;

import a.A;

public class B {
    void method2Move() {
      new A.I() {
        {
          super.foo();
          foo();
          A.bar();
        }
      }
    }
}