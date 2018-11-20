import java.util.function.*;

public class SwitchExpressionsJava12 {
  static void fooBar(int k) {
    String s = switch (k) {
      case 1, 2 -> "foo";
      default -> "bar";
    };
    if(<warning descr="Condition 's.equals(\"baz\")' is always 'false'">s.equals("baz")</warning>) {}
  }

  enum X {A, B, C};
  
  static void testEnum(X x) {
    int i = switch(<error descr="'switch' expression does not cover all possible input values">x</error>) {
      case A -> 1;
      case B -> 2;
    };
    
    if (i == 0) {} // default and case C is missing: we assume that any other result is also possible

    int i1 = switch(x) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };

    if (<warning descr="Condition 'i1 == 0' is always 'false'">i1 == 0</warning>) {} // exhaustive
  }

  static void testBoxed(int i) {
    Integer j = switch(i) {
      case 1 -> 2;
      case 2 -> i+3;
      case 3 -> i;
      default -> 4;
    };
    if (<warning descr="Condition 'j > 1' is always 'true'">j > 1</warning>) {}
    if (<warning descr="Condition 'j < 6' is always 'true'">j < 6</warning>) {}
    if (j < 5) {}
  }

  static void testBoxedThrow(int i) {
    Integer j = switch(i) {
      case 1 -> 2;
      case 2 -> i+3;
      case 3 -> i;
      default -> throw new IllegalArgumentException();
    };
    if (<warning descr="Condition 'j > 1' is always 'true'">j > 1</warning>) {}
    if (<warning descr="Condition 'j < 6' is always 'true'">j < 6</warning>) {}
    if (j < 5) {}
    if (<warning descr="Condition 'j == 5 && i != 2' is always 'false'">j == 5 && <warning descr="Condition 'i != 2' is always 'false' when reached">i != 2</warning></warning>) {}
    if (<warning descr="Condition 'i > 3' is always 'false'">i > 3</warning>) {}
    if (<warning descr="Condition 'i < 1' is always 'false'">i < 1</warning>) {}
  }

  static void testSwitchInInlinedLambda(int i) {
    int x = ((IntSupplier)() -> {
      int j = switch (i) {
        case 1 -> 2;
        case 2 -> 3;
        case 3 -> {
          System.out.println("hello");
          return 1; // exit lambda, not switch!
        }
        default -> 4;
      };
      if (<warning descr="Condition 'j < 2' is always 'false'">j < 2</warning>) {}
      if (<warning descr="Condition 'j == 4 && i == 2' is always 'false'">j == 4 && <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {}
      if (<warning descr="Condition 'i == 3' is always 'false'">i == 3</warning>) {}
      return 0;
    }).getAsInt();
    if (x == 1 && <warning descr="Condition 'i == 3' is always 'true' when reached">i == 3</warning>) {}
  }

  static void testSwitchInInlinedLambdaWithInnerFinally(int i) {
    int x = ((IntSupplier)() -> {
      int j = switch (i) {
        case 1 -> 2;
        case 2 -> 3;
        case 3 -> {
          try {
            System.out.println("hello");
            return 1;
          }
          finally {
            return 2;
          }
        }
        default -> 4;
      };
      if (<warning descr="Condition 'j < 2' is always 'false'">j < 2</warning>) {}
      if (<warning descr="Condition 'j == 4 && i == 2' is always 'false'">j == 4 && <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {}
      if (<warning descr="Condition 'i == 3' is always 'false'">i == 3</warning>) {}
      return 0;
    }).getAsInt();
    if (<warning descr="Condition 'x == 1 && i == 3' is always 'false'"><warning descr="Condition 'x == 1' is always 'false'">x == 1</warning> && i == 3</warning>) {}
    if (x == 2 && <warning descr="Condition 'i == 3' is always 'true' when reached">i == 3</warning>) {}
  }

  static void testSwitchInInlinedLambdaWithOuterFinally(int i) {
    int x = ((IntSupplier)() -> {
      int j = 0;
      try {
        j = switch (i) {
              case 1 -> 2;
              case 2 -> 3;
              case 3 -> {
                System.out.println("hello");
                return 1;
              }
              default -> 4;
            };
      }
      finally {
        if (j == 0) {
          return 2;
        }
      }
      if (<warning descr="Condition 'j < 2' is always 'false'">j < 2</warning>) {}
      if (<warning descr="Condition 'j == 4 && i == 2' is always 'false'">j == 4 && <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {}
      if (<warning descr="Condition 'i == 3' is always 'false'">i == 3</warning>) {}
      return 0;
    }).getAsInt();
    if (<warning descr="Condition 'x == 1 && i == 3' is always 'false'"><warning descr="Condition 'x == 1' is always 'false'">x == 1</warning> && i == 3</warning>) {}
    if (x == 2 && <warning descr="Condition 'i == 3' is always 'true' when reached">i == 3</warning>) {}
  }

  static int get(int x) {
    return x;
  }

  void testStatementInsideExpressionInsideBlockLambda(int x, int y) {
    int i = ((IntSupplier)() -> {
      System.out.println();
      return switch(x) {
        case 1 -> {
          switch (y) {
            default -> get(x);
          }
          return 5;
        }
        default -> 10;
      };
    }).getAsInt();
    if (i != 10) {}
  }

  void testSwitchWithReturnInsideExpressionLambda(int x) {
    int i = ((IntSupplier)() -> x < 0 ? 0 : switch(x) {
      case 1 -> { return 2; }
      default -> 3;
    }).getAsInt();
    if (i == 2 && <warning descr="Condition 'x == 1' is always 'true' when reached">x == 1</warning>) {}
  }
}
