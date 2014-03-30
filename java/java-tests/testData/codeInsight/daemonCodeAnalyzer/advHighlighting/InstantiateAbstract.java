// instantiate abstract
public class a {
  void f() {
    <error descr="'ii' is abstract; cannot be instantiated">new ii()</error>;

    <error descr="'c1' is abstract; cannot be instantiated">new c1()</error>;

    new <error descr="Class 'Anonymous class derived from c1' must either be declared abstract or implement abstract method 'f1(int)' in 'c1'">c1</error>() {
      public void f2() {}
    };

    new <error descr="Class 'Anonymous class derived from c1' must either be declared abstract or implement abstract method 'f1(int)' in 'c1'">c1</error>() {
      public void f1() {}
    };

    new <error descr="Class 'Anonymous class derived from c1' must either be declared abstract or implement abstract method 'f2()' in 'Anonymous class derived from c1'">c1</error>() {
      public void f1(int i) {}
      public <error descr="Abstract method in non-abstract class">abstract</error> void f2();
    };


    new c1() {
      public void f1(int i) {}
    };
    new ii() { public void f1(){} public void f2(){} };

  }
}
abstract class c1 {
  abstract public void f1(int i);
  Object o = new c1[0];
}

interface ii {
  abstract void f1();
  void f2();
}

