class X {

  static class Nested {
    static void <caret>print(X x) {
      System.out.println(x);
    }
  }

  public static void main(String[] args) {
    Nested.print(new X());
  }
}