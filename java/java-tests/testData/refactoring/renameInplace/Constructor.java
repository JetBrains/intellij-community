public class Constructor {
  public Constructor() {
  }
  void foo() {
    final Constructor constructor = new Co<caret>nstructor();
    System.out.println(constructor);
  }
}