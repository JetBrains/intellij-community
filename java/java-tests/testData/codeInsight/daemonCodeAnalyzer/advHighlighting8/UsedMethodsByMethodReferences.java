class C {
  interface Simplest {
    void m();
  }
  private static void simplest1() { }
  private static void <warning descr="Private method 'simplest()' is never used">simplest</warning>() { }

  public static void main(String[] args) {
    Simplest o = C::simplest1;
    System.out.println(o);
  }
}