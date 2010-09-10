public class Parent {
  public void foo(float j, int i, String s) {
    System.out.println(j + s + i);
  }
}

class Child extends Parent {
  public void foo(float j, <selection> int i,</selection> String s ) {
  }

  void bar() {
    foo(1.0, 1, "aaa");
  }
}