class X {

  class Inner {
    static void <caret>print(X x) {
      System.out.println(x);
    }
  }

  public static void main(String[] args) {
    Inner.print(new X());
  }
}