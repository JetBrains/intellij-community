import java.util.*;

class Test {
  static int FOO;
  
  void test() {
    FOO = 5;
    new A();
    if (<warning descr="Condition 'FOO == 5' is always 'true'">FOO == 5</warning>) {}

    FOO = 6;
    new B();
    if (FOO == 6) {}

    FOO = 7;
    new C();
    if (<warning descr="Condition 'FOO == 7' is always 'true'">FOO == 7</warning>) {}

    FOO = 8;
    new D();
    if (FOO == 8) {}
  }
  
  class A {
    static final int x = 5;
    int y;
  }
  class B {
    int z = FOO++;
  }
  class C extends A {
    
  }
  class D extends B {
    
  }
}