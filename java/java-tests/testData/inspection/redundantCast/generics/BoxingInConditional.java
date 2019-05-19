//This is a test for JDK_15 LanguageLevel
class Test {
  private Integer foo(String s, Integer i) {
    return s == null ? i : (Integer)2;
  }

  private int foo1(String s, Integer i) {
    return s == null ? i : (<warning descr="Casting '2' to 'Integer' is redundant">Integer</warning>)2;
  }
}

class RedundantCastTest {

  public static void main(String[] args) {
    Integer foo = 5;
    Integer bar = null;
    print(args == null || args.length == 0 ? bar : (Integer) 1);
    int i = args == null || args.length == 0 ? bar : (<warning descr="Casting '1' to 'Integer' is redundant">Integer</warning>) 1;
  }

  private static void print(Integer i) {}
}