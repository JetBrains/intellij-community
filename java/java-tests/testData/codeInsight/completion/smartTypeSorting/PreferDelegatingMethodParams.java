public class A {
  private A myDelegate;

  public void method(String abc, String xyz) {
    myDelegate.method(abc, <caret>);
  }
}