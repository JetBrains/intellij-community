class Test {
  int testIncomplete(Object obj) {
    return <error descr="Switch expression should produce a result in all execution paths">switch</error>(obj) {
      case String s when<EOLError descr="Expression expected"></EOLError><EOLError descr="':' or '->' expected"></EOLError>
    };
  }

  int test3(String s1) {
    return switch(s1) {
      case String s when s.length() <= 3 && (s.length() > 1 || <warning descr="Condition 's.length() > 10' is always 'false' when reached">s.length() > 10</warning>) -> 1;
      default -> 3;
    };
  }

  int test4(String s) {
    return switch (s) {
      case String ss when (ss.length() < 3 || ss.length() == 4) -> 1;
      default -> 3;
    };
  }

  int test5(Object o) {
    return switch(o) {
      case String s when s.length() > 5 -> 1;
      case String s1 when <warning descr="Condition 's1.length() > 10' is always 'false'">s1.length() > 10</warning> -> 2;
      default -> 3;
    };
  }
}