import java.util.List;

class Test {
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

  void testInstanceofInForeach(List<Object> list) {
    for (Object obj : list) {
      if (obj instanceof String st) {
      }
    }
  }

  void testInstanceofTotalInForeach(List<Object> list) {
    for (Object obj : list) {
      if (obj instanceof <error descr="Pattern type 'Object' is the same as expression type">Object</error> st) {
      }
    }
  }

  private String getString() {
    return "str";
  }
}