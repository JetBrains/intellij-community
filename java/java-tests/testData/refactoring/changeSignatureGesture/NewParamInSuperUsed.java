public class Parent {
  public void foo(int i) {
    System.out.println(i);
  }
}

class Child extends Parent {
  public void foo(int i<caret>) {
  }

  void bar() {
    foo(1);
  }
}