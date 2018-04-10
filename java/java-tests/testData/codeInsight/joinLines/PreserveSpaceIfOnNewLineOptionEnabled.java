class A {
  public void foo() {
    try {
      System.out.println("Hello");
    }<caret>
    catch (Exception e) {
      System.err.println("Error");
    }
  }
}