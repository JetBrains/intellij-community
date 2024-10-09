class A {
  private Object b = new Inner();

  private class <caret>Inner {
    static String A;

    public String toString() {
      return "A";
    }
    
    public static void staticMethod() {
      A = "";
      System.out.println(A);
    }
    
    static class InnerInner {}

    static {
      // static initializer
    }
  }
}