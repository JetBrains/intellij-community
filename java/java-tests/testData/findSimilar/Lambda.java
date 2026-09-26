package aPackage;

class A {
  public int foo(Runnable run) {
    return 0;
  }

  public static void main(String[] args) {
    A a1 = new A();
    int a = a1.fo<caret>o(()->{});
  }
}