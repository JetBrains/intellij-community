abstract class Test extends Sup<caret>er {
  {
    System.out.println(CONST);
    System.out.println(Super.CONST);
  }
}
abstract class Test1 extends Super {
  {
    System.out.println(CONST);
    System.out.println(Super.CONST);
  }
}