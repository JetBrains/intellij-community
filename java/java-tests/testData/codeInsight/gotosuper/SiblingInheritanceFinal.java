package z;

interface I {
  void run();
}
abstract class A {
  public final void <caret>run() { someCode(); }
}

class Foo extends A implements I {
}