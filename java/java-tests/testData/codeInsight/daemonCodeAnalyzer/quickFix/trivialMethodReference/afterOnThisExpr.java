// "Replace with qualifier" "true-preview"
class A implements Runnable {
  public void run() {}
}

class B extends A {
  void f(Runnable r) {}

  {
    f(this);
  }
}