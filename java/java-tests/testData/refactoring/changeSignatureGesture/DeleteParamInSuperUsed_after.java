public class Parent {
  public void foo(float j, String s) {
    System.out.println(j + s + i);
  }
}

class Child extends Parent {
  public void foo(float j, String s) {
  }

  void bar() {
    foo(1.0,  "aaa");
  }
}