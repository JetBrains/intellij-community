public class Bar {
  public Bar() {
  }
  void foo() {
    final Bar bar = new Bar();
    System.out.println(bar);
  }
}