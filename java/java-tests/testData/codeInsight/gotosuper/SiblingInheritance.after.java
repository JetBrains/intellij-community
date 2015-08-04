package z;

interface I {
  void <caret>run();
}
abstract class A {
  public void run() {}
}

class Foo extends A implements I {
}