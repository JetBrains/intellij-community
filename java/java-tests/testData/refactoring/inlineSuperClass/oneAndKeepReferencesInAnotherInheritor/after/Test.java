abstract class Test extends Sup<caret>er {
    public static final String CONST = "CONST";

    {
    System.out.println(CONST);
    System.out.println(Test.CONST);
  }
}
abstract class Test1 extends Super {
  {
    System.out.println(CONST);
    System.out.println(Super.CONST);
  }
}