public class A {
  public void doSmth() {
    UniqueClassName[] items = new UniqueClassName[10];
  }

  public static class UniqueClassName {
  }
}
class B{
    public void doSmth() {
        A.UniqueClassName[] items = new A.UniqueClassName[10];
    }<caret>
}