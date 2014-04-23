class Test {
  interface I { Object invoke(); }
  interface IStr { String foo(); }

  private static void call(IStr str) {
    System.out.println(str);
  }

  private static void <warning descr="Private method 'call(Test.I)' is never used">call</warning>(I i) {
    System.out.println(i);
  }

  public static void main(String[] args)   {
      call(()-> null);
  }
}