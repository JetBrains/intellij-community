public class Parent {
  public void foo(int i, int param) {
    System.out.println(i);
  }
}

class Child extends Parent {
  public void foo(int i, int param) {
  }
}