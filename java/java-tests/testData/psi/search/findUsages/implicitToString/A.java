class Foo {

  public static void main(String[] args) {
    System.out.println(new Foo() + "");
  }

  @Override
  public String toString() {
    return "Foo";
  }
}