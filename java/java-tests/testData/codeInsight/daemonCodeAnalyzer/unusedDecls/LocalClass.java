class Test {
  public static void main(String[] args) {
    class Inner1{};
    class <warning>Inner2</warning> {};
    new Inner1();
  }
}
