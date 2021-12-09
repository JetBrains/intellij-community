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

  void endlessLoopInBranchWithValueBreak(String arg) {
    int result = switch (arg) {
      case "one" -> { while(true); <error descr="Unreachable statement">yield</error> 1;}
      default -> 0;
    };
    System.out.println(result);
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
      case "a": n = 1; yield 1;
      default: n = 0; yield 0;
    };
  }

  int returnSwitchExpression(String s) {
    return switch(s) {
      case "a" -> 1;
      default -> 0;
    };
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void switchExpressionUnreachableInFinally(int n) {
    String s;
    try {
    } finally {
      return;
      s = <error descr="Unreachable statement">switch</error> (n) {
        case 0 -> "a";
        default -> "b";
      };
    }
  }

  void switchExpressionReachableInFinally(int n) {
    String s;
    try {
      return;
    } finally {
      s = switch (n) {
        case 0 -> "a";
        default -> "b";
      };
    }
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

  static class ValueBreakSwitchExpressionReturnedFromTry {
    int foo(String s) throws Exception {
      try {
        return switch (s) {
          case "a": yield bar(1);
          default: yield bar(0);
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