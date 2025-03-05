class C {
  void finalVariableAssignedInAllBranches(int k) {
    final String s;
    switch (k) {
      case 1 -> s = "a";
      case 2 -> s = "b";
      case 3, 4 -> s = "c";
      default -> s = "d";
    }
    System.out.println(s);
  }

  enum EnumAB {A, B}
  void finalVariableAssignedInAllEnumConstantBranches(EnumAB ab) {
    final int n;
    switch (ab) {
      case A -> n = 1;
      case B -> n = 2;
    }
    System.out.println(<error descr="Variable 'n' might not have been initialized">n</error>);
  }

  void assignedInSomeBranches(String s) {
    int n;
    switch ((int)Math.random()) {
      case 1 -> n = 1;
      default -> {}
    }
    System.out.println(<error descr="Variable 'n' might not have been initialized">n</error>);
  }

  void finalVariableReassignedAfterSwitchStatement(int n) {
    final String s;
    switch (n) {
      case 1 -> s = "a";
      default -> {}
    }
    <error descr="Variable 's' might already have been assigned to">s</error> = "b";
    System.out.println(s);
  }

  void finalVariableReassignedAfterSwitchExpression(int n) {
    final String s;
    String t = switch (n) {
      case 1 -> s = "a";
      default -> "";
    };
    <error descr="Variable 's' might already have been assigned to">s</error> = t;
    System.out.println(s);
  }

  void finalVariableReassignedInSwitchStatement(int n) {
    final String s = "b";
    switch (n) {
      case 1 -> <error descr="Cannot assign a value to final variable 's'">s</error> = "a";
      default -> {}
    };
    System.out.println(s);
  }

  void finalVariableReassignedInSwitchExpression(int n) {
    final String s = "b";
    String string = switch (n) {
      case 1 -> <error descr="Cannot assign a value to final variable 's'">s</error> = "a";
      default -> "";
    };
    System.out.println(s);
  }


  static class FinalFieldAssignedInSomeBranches {
    <error descr="Field 'n' might not have been initialized">final int n</error>;
    {
      switch ((int)Math.random()) {
        case 1 -> n = 1;
        default -> {}
      }
    }
  }

  static class FinalFieldAssignedInSomeBranchesNoDefault {
    <error descr="Field 'n' might not have been initialized">final int n</error>;
    {
      switch ((int)Math.random()) {
        case 1 -> n = 1;
        case 0 -> n = 0;
      }
    }
  }

  static class FinalFieldAssignedInAllBranches {
    final int n;
    {
      switch ((int)Math.random()) {
        case 1 -> n = 1;
        default -> n = 0;
      }
    }
  }

  static class FinalFieldInitializedWithswitchExpression {
    final int n =
      switch ((int)Math.random()) {
        case 1 -> 1;
        default -> 0;
      };
  }

  static class FinalFieldSwitchExpression {
    final String s = switch ((int)Math.random()) {
      case 1 -> "a";
      default -> "?";
    };
    {
      System.out.println(s);
    }
  }

  static class FinalFieldValueBreakSwitchExpression {
    final String s = switch ((int)Math.random()) {
      case 1: yield "a";
      default: yield "?";
    };
    {
      System.out.println(s);
    }
  }

  void finalVariableSwitchExpression(String s) {
    final int n = switch (s) {
      case "a" -> 1;
      default -> 0;
    };
    System.out.println(n);
  }

  void finalVariableValueBreakSwitchExpression(String s) {
    final int n = switch (s) {
      case "a": yield 1;
      default: yield 0;
    };
    System.out.println(n);
  }

  void definitelyAssignedInSwitchExpression(String s) {
    int n;
    int x = switch (s) {
      case "a" -> n = 1;
      default -> n = 0;
    };
    System.out.println(n);
  }

  void notDefinitelyAssignedInSwitchExpression(String s) {
    int n;
    int x = switch (s) {
      case "a" -> n = 1;
      default -> 0;
    };
    System.out.println(<error descr="Variable 'n' might not have been initialized">n</error>);
  }

  void definitelyAssignedInSwitchExpressionValueBreak(String s) {
    int n;
    int x = switch (s) {
      case "a": yield n = 1;
      default: yield n = 0;
    };
    System.out.println(n);
  }

  void notDefinitelyAssignedInSwitchExpressionValueBreak(String s) {
    int n;
    int x = switch (s) {
      case "a": yield n = 1;
      default: yield 0;
    };
    System.out.println(<error descr="Variable 'n' might not have been initialized">n</error>);
  }

  void switchExpressionAssignedInFinally(int n) {
    String s;
    try {
    } finally {
      s = switch (n) {
        case -1 -> throw new RuntimeException();
        case 0 -> "a";
        default -> "b";
      };
    }
    System.out.println(s);
  }

  void allSwitchRulesAssignInFinally(int n) {
    String s;
    try {
    } finally {
      String string = switch (n) {
        case -1 -> throw new RuntimeException();
        case 0 -> s = "a";
        default -> { yield s = "b"; }
      };
    }
    System.out.println(s);
  }

  void notAllSwitchRulesAssignInFinally(int n) {
    String s;
    try {
    } finally {
      String t = switch (n) {
        case -1 -> throw new RuntimeException();
        case 0 -> s = "a";
        default -> "b";
      };
    }
    System.out.println(<error descr="Variable 's' might not have been initialized">s</error>);
  }

  sealed interface T permits T1, T2 {}
  final class T1 implements T {}
  final class T2 implements T {}

  private void testStatement1(T obj) {
    int i;
    switch (obj) {
      case T1 t1 -> i = 1;
      case T2 t2 -> i = 2;
    };
    System.out.println(i);
  }

  private void testStatement2(int obj) {
    int i;
    switch (obj) {
      case 1 -> i = 1;
      case 2 -> i = 2;
    };
    System.out.println(<error descr="Variable 'i' might not have been initialized">i</error>);
  }

  private void testStatement3(Integer obj) {
    int i;
    switch (obj) {
      case 1 -> i = 1;
      case 2 -> i = 2;
    };
    System.out.println(<error descr="Variable 'i' might not have been initialized">i</error>);
  }

  private void testExpressions1(T obj) {
    int i;
    int y = switch (obj) {
      case T1 t1 -> {i = 1; yield 1;}
      case T2 t2 -> {i = 2; yield 2;}
    };
    System.out.println(i);
  }
}