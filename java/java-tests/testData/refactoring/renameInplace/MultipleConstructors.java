public class Constructor {
  public Constructor() {
  }

  public Constructor(int i) {
    System.out.println(i);
  }

  void foo() {
    final Constructor constructor = new Co<caret>nstructor();
    System.out.println(constructor);
  }
}