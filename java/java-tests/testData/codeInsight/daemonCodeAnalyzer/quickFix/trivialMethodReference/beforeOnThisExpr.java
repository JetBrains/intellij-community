// "Replace with qualifier" "true"
class A implements Runnable {
  public void run() {}
}

class B extends A {
  void f(Runnable r) {}

  {
    f(this::ru<caret>n);
  }
}