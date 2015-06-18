class Test {
  interface I { Object in<EOLError descr="';' expected"></EOLError>
    <error descr="Invalid method declaration; return type required">voke</error>(); }
  interface IStr { String foo(); }

  public static void call(IStr str) {}
  public static void call(I i) {  }

  public static void main(String[] args)   {
      <error descr="Ambiguous method call: both 'Test.call(IStr)' and 'Test.call(I)' match">call</error>(()-> null);
  }
}