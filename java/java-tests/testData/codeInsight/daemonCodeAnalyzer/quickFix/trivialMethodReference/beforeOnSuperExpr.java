// "Replace with qualifier" "false"
class A implements Runnable {
  public void run() {}
}

class B extends A {
  void f(Runnable r) {}

  {
    f(super::ru<caret>n);
  }
}