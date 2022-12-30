class Test {
  int testIncomplete(Object obj) {
    return switch(obj) {
      case String s when<EOLError descr="Expression expected"></EOLError><EOLError descr="':' expected"></EOLError>
    };
  }

  int test3(String s1) {
    return switch(s1) {
      case String s when s.length() <= 3 && (s.length() > 1 || <warning descr="Condition 's.length() > 10' is always 'false' when reached">s.length() > 10</warning>) -> 1;
      case default -> 3;
    };
  }

  int test4(String s) {
    return switch (s) {
      case String ss when (ss.length() < 3 || ss.length() == 4) -> 1;
      case default -> 3;
    };
  }

  int test5(Object o) {
    return switch(o) {
      case String s when s.length() > 5 -> 1;
      case <warning descr="Switch label 'String s1 when s1.length() > 10' is unreachable">String s1 when <warning descr="Condition 's1.length() > 10' is always 'false'">s1.length() > 10</warning></warning> -> 2;
      default -> 3;
    };
  }
}