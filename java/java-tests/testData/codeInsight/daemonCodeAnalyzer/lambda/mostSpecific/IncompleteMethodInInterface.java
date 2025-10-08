class Test {
  interface I { Object in<EOLError descr="';' expected"></EOLError>
    <error descr="Method return type missing">voke</error>(); }
  interface IStr { String foo(); }

  public static void call(IStr str) {}
  public static void call(I i) {  }

  public static void main(String[] args)   {
      call<error descr="Ambiguous method call: both 'Test.call(IStr)' and 'Test.call(I)' match">(()-> null)</error>;
  }
}