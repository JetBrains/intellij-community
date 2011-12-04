public class Constructor {
  public Constructor() {
  }

  public Constructor(int i) {
    System.out.println(i);
  }

  void foo() {
    final Constr<caret>uctor constructor = new Constructor();
    System.out.println(constructor);
  }
}