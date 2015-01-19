package b;

import a.A;

public class B {
    void method2Move() {
      new A.I() {
        {
          foo();
          A.bar();
        }
      }
    }
}