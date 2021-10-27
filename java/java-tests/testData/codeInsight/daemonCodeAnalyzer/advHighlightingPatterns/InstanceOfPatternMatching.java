
class Main {

  private static final boolean TRUE = 1 == 1;

  void testGuardedPatternWithCompileTimeCondition1(String s) {
    if (s instanceof (<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && true)) {
      System.out.println(s1);
    }
  }

  void testGuardedPatternWithAlwaysTrueCondition2(String s) {
    if (s instanceof (<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && TRUE)) {
      System.out.println(s1);
    }
  }

  void testGuardedPatternWithAlwaysTrueCondition3(String s) {
    if (s instanceof ((<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && true) && true)) {
      System.out.println(s1);
    }
  }

}