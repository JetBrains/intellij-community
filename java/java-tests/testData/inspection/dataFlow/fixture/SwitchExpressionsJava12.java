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

  static void testIncomplete(Integer i) {
    if (i == null) {
      System.out.println(switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>)<error descr="'{' expected">)</error>;
    } else {
      int x = switch(i) {
        case 1 -><EOLError descr="Expression, block or throw statement expected"></EOLError>
        case 2 -> {
          if (<warning descr="Condition 'i == 1' is always 'false'">i == 1</warning>) {}
          throw new IllegalArgumentException();
        }  
        default -> 1;
      };
      if (x != 1) {
        // the only case where we don't know the returned value is i == 1, so the warning is logical here
        if (<warning descr="Condition 'i == 1' is always 'true'">i == 1</warning>) {}
      }
    }
  }
}
