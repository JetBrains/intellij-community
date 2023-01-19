final class Test {
  int test1(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 42;
      default -> 42;
    } : switch (num) {
      case 42 -> 42;
      default -> (42);
    });
  }

  int test2(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (0);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 42;
      default -> 42;
    } : switch (num) {
      case 42 -> 42;
      default -> (42);
    });
  }

  int test3(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (0) : 42;
      default -> 42;
    } : switch (num) {
      case 42 -> 42;
      default -> (42);
    });
  }

  int test4(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 0;
      default -> 42;
    } : switch (num) {
      case 42 -> 42;
      default -> (42);
    });
  }

  int test5(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 42;
      default -> 0;
    } : switch (num) {
      case 42 -> 42;
      default -> (42);
    });
  }

  int test6(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 42;
      default -> 42;
    } : switch (num) {
      case 42 -> 0;
      default -> (42);
    });
  }

  int test7(int num, boolean flag1, boolean flag2, boolean flag3) {
    if (flag1) return (42);
    return (flag2 ? switch (num) {
      case 1 -> flag3 ? (42) : 42;
      default -> 42;
    } : switch (num) {
      case 42 -> 42;
      default -> (0);
    });
  }
}