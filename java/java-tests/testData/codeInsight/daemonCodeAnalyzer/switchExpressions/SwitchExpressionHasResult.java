class C {
  void tryFinally(int n) {
    int s = switch(n) {
      case 2 -> {
        System.out.println("hello");
        yield 1;
      }
      case 3 -> {
        try {
          System.out.println("hello");
          yield 1; // never happens
        }
        finally {
          yield 2;
        }
      }
      default -> 0;
    };
  }
  
  void defaultBranchHasNoResult(int n) {
    String s = <error descr="Switch expression should produce a result in all execution paths">switch</error> (n) {
      default:
    };
  }

  void defaultRuleHasNoResult(int n) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce a result in all execution paths">default</error> -> {}
    };
  }

  void defaultBranchSometimesHasNoResult(int n, boolean b) {
    String s = <error descr="Switch expression should produce a result in all execution paths">switch</error> (n) {
      default: {
        if (b) yield "";
      }
    };
  }

  void defaultRuleSometimesHasNoResult(int n, boolean b) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce a result in all execution paths">default</error> -> {
        if (b) yield "";
      }
    };
  }

  void defaultBranchAlwaysThrows(int n) {
    String s = <error descr="'switch' expression does not have any result expressions">switch</error> (n) {
      default: throw new RuntimeException();
    };
  }

  void defaultRuleAlwaysThrows(int n) {
    String s = <error descr="'switch' expression does not have any result expressions">switch</error> (n) {
      default -> throw new RuntimeException();
    };
  }

  void defaultBranchSometimesThrows(int n, boolean b) {
    String s = switch (n) {
      default:
        if (b) throw new RuntimeException();
        yield "";
    };
  }

  void defaultRuleSometimesThrows(int n, boolean b) {
    String s = switch (n) {
      default -> {
        if (b) throw new RuntimeException();
        yield "";
      }
    };
  }

  void defaultBranchHasManyResults(int n, int k) {
    String s = switch (n) {
      default: {
        if (k < n) yield "a";
        if (k > n) yield "b";
        yield "c";
      }
    };
  }

  void defaultRuleHasManyResults(int n, int k) {
    String s = switch (n) {
      default -> {
        if (k < n) yield "a";
        if (k > n) yield "b";
        yield "c";
      }
    };
  }

  void oneOfBranchesHasNoResult(int n) {
    String s = <error descr="Switch expression should produce a result in all execution paths">switch</error> (n) {
      case 0: yield "";
      default:
    };
  }

  void oneOfRulesHasNoResult(int n) {
    String s = switch (n) {
      case 0 -> "";
      <error descr="Switch expression rule should produce a result in all execution paths">default</error> -> {
      }
    };
  }

  void allBranchesHaveNoResult(int n) {
    String s = <error descr="Switch expression should produce a result in all execution paths">switch</error> (n) {
      case 0:
      default:
    };
  }

  void allRulesHaveNoResult(int n) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce a result in all execution paths">case</error> 0 -> {
      }
      <error descr="Switch expression rule should produce a result in all execution paths">default</error> -> {
      }
    };
  }

  void allBranchesDoHaveResult(int n) {
    String s = switch (n) {
      case -1: throw new RuntimeException();
      case 0: yield "a";
      default: yield "b";
    };
  }

  void allRulesDoHaveResult(int n) {
    String s = switch (n) {
      case -1 -> throw new RuntimeException();
      case 0 -> "a";
      default -> "b";
    };
  }

  void allBranchesDoHaveResultInFinally(int n) {
    String s;
    try {
    } finally {
      s = switch (n) {
        case -1: throw new RuntimeException();
        case 0: yield "a";
        default: yield "b";
      };
    }
  }

  void allRulesDoHaveResultInFinally(int n) {
    String s;
    try {
    } finally {
      s = switch (n) {
        case -1 -> throw new RuntimeException();
        case 0 -> "a";
        default -> "b";
      };
    }
  }
  
  void systemExit(int x) {
    int result = switch(x) {
      <error descr="Switch expression rule should produce a result in all execution paths">case</error> 1 -> {
        System.exit(0);
      }
      default -> 0;
    };
  }
}