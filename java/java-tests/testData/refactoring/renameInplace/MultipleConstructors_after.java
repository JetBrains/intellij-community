public class Bar {
  public Bar() {
  }

  public Bar(int i) {
    System.out.println(i);
  }

  void foo() {
    final Bar bar = new Bar();
    System.out.println(bar);
  }
}