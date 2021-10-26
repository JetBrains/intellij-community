class Test {

  private static final boolean TRUE = <warning descr="Condition '1 == 1' is always 'true'">1 == 1</warning>;

  void testTypePattern1(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof String s' is always 'true'">o instanceof String s</warning>) {
      System.out.println();
    }
  }

  void testPattern2(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof Integer i' is always 'false'">o instanceof Integer i</warning>) {
      System.out.println();
    }
  }

  void testGuardedPattern1(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof ((String s && s.length() <= 3) && (s.length() > 1 || s.length() > 10))' is always 'true'">o instanceof ((String s && <warning descr="Condition 's.length() <= 3' is always 'true'">s.length() <= 3</warning>) && (<warning descr="Condition 's.length() > 1 || s.length() > 10' is always 'true'"><warning descr="Condition 's.length() > 1' is always 'true'">s.length() > 1</warning> || s.length() > 10</warning>))</warning>) {
      System.out.println();
    }
  }

  void testGuardedPattern2(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof (String s && s.length() < 3)' is always 'false'">o instanceof (String s && <warning descr="Condition 's.length() < 3' is always 'false'">s.length() < 3</warning>)</warning>) {
      System.out.println();
    }
  }

  void testParenthesizedPattern1(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof (String s)' is always 'true'">o instanceof (String s)</warning>) {
      System.out.println();
    }
  }

  void testParenthesizedPattern2(Object o) {
    o = "fsd";
    if (<warning descr="Condition 'o instanceof (Integer i)' is always 'false'">o instanceof (Integer i)</warning>) {
      System.out.println();
    }
  }

  void testParenthesizedPattern(String s) {
    if (s instanceof (<error descr="Pattern type 'String' is the same as expression type">String</error> s1)) {
      System.out.println(s1);
    }
  }

  void testDeepParenthesizedPattern(String s) {
    if (s instanceof ( ((( ((  <error descr="Pattern type 'String' is the same as expression type">String</error> s1)) ))     ) )) {
      System.out.println(s1);
    }
  }

  void testGuardedPatternWithCompileTimeCondition1(String s) {
    if (<warning descr="Condition 's instanceof (String s1 && true)' is always 'true'">s instanceof (<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && true)</warning>) {
      System.out.println(s1);
    }
  }

  void testGuardedPatternWithAlwaysTrueCondition2(String s) {
    if (s instanceof (<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && TRUE)) {
      System.out.println(s1);
    }
  }

  void testGuardedPatternWithAlwaysTrueCondition3(String s) {
    if (<warning descr="Condition 's instanceof ((String s1 && true) && true)' is always 'true'">s instanceof ((<error descr="Pattern type 'String' is the same as expression type">String</error> s1 && true) && true)</warning>) {
      System.out.println(s1);
    }
  }

  private String getString() {
    return "str";
  }
}