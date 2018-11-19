class C {
  void alwaysThrow(String s) {
    switch (s) {
      case "a" -> throw new IllegalArgumentException();
      default -> throw new IllegalStateException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void breakFromEndlessLoop() {
    EndlessLoop:
    for (;;) {
      switch ((int)Math.random()) {
        case 1 -> {break EndlessLoop;}
        default -> throw new RuntimeException();
      }
    }
    System.out.println();
  }

  void continueEndlessLoop() {
    EndlessLoop:
    for (;;) {
      switch ((int)Math.random()) {
        case 1 -> {continue EndlessLoop;}
        default -> throw new RuntimeException();
      }
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void endlessLoopsInAllBranches(String s) {
    switch (s) {
      case "a" -> { while(true); }
      default -> { for(;;); }
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void endlessLoopInBranch(String s) {
    switch (s) {
      case "a" -> { while(true); }
      default -> {}
    };
    System.out.println();
  }

  /* todo
  void endlessLoopInBranchWithValue(String arg) {
    int result = switch (arg) {
      case "one" -> { while(true); break 1;}
      default -> 0;
    };
    System.out.println(result);
  }
  */

  static class FinalFieldSwitchExpression {
    final String s = switch ((int)Math.random()) {
      case 1 -> "a";
      default -> "?";
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

  void notDefinitelyAssigned(String s) {
    int n;
    switch (s) {
      case "a" -> n = 1;
    }
    System.out.println(<error descr="Variable 'n' might not have been initialized">n</error>);
  }

  int returnBeforeEnhancedSwitchStatement(String s) {
    return 2;
    <error descr="Unreachable statement">switch</error>(s) {
    case "a" -> {return 1;}
    default -> {return 0;}
    }
  }

  int returnBeforeSwitchExpressionInInitializer(String s) {
    return 2;
    int n = <error descr="Unreachable statement">switch</error>(s) {
      case "a" -> 1;
      default -> 0;
    };
  }

  int returnBeforeSwitchExpressionInAssignment(String s) {
    int n;
    return 2;
    n = <error descr="Unreachable statement">switch</error>(s) {
      case "a": n= 1;break;
      default: n= 0;
    };
  }

  int returnSwitchExpression(String s) {
    return switch(s) {
      case "a" -> 1;
      default -> 0;
    };
    <error descr="Unreachable statement">System.out.println();</error>
  }

  static class SwitchExpressionReturnedFromTry {
    int foo(String s) throws Exception {
      try {
        return switch (s) {
          case "a" -> bar(1);
          default -> bar(0);
        };
      } finally {
        System.out.println("b");
      }
      <error descr="Unreachable statement">System.out.println("c");</error>
    }
    int bar(int i) throws Exception { return i; }
  }

  static class SwitchStatementReturnsFromTry {
    int foo(String s) throws Exception {
      try {
        switch (s) {
          case "a" -> { return bar(1); }
          default -> { return bar(0); }
        }
      } finally {
        System.out.println("b");
      }
      <error descr="Unreachable statement">System.out.println("c");</error>
    }
    int bar(int i) throws Exception { return i; }
  }
}