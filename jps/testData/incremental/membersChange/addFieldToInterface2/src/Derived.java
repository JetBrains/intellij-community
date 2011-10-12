class Derived extends Base implements BaseInterface {
  void foo() {
    System.out.println(field);
  }

  public static void main(String[] args) {
    new Derived().foo();
  }
}