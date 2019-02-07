import java.util.Random;

class SwitchExpressions {
  enum E { E1, E2 }

  void m() {
    System.out.println(switch (new Random().nextInt()) {
      default -> "whatever";
    });

    System.out.println(switch (<error descr="'switch' expression does not have any case clauses">new Random().nextInt()</error>) { });

    System.out.println(switch (new Random().nextInt()) {
      case 0 -> throw new IllegalStateException("no args");
      <error descr="Different case kinds used in the switch">case 1:</error> break "lone";
    });

    System.out.println(
      switch (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'char, byte, short, int, Character, Byte, Short, Integer, String, or an enum'">new Object()</error>) {
        default -> "whatever";
      }
    );

    System.out.println(switch (E.valueOf("E1")) {
      case <error descr="Constant expression required">null</error> -> 0;
      case <error descr="An enum switch case label must be the unqualified name of an enumeration constant">E.E1</error> -> 1;
      case E2 -> 2;
      case <error descr="Incompatible types. Found: 'int', required: 'SwitchExpressions.E'">1</error> -> 1;
    });

    System.out.println(switch (new Random().nextInt()) {
      <error descr="Duplicate default label">default</error> -> -1;
      case 1 -> 1;
      <error descr="Duplicate default label">default</error> -> 0;
    });

    System.out.println(switch (<error descr="'switch' expression does not cover all possible input values">new Random().nextInt()</error>) {
      case 1 -> 1;
    });
    System.out.println(switch (<error descr="'switch' expression does not cover all possible input values">E.valueOf("E1")</error>) {
      case E1 -> 1;
    });
    System.out.println(switch (E.valueOf("E1")) {
      case E1 -> 1;
      case E2 -> 2;
    });

    lab: while (true) {
      switch (new Random().nextInt()) {
        case -1: return;
        case -2: continue lab;
        default: break lab;
      }
      System.out.println(switch (new Random().nextInt()) {
        case -1: <error descr="Return outside of enclosing switch expression">return;</error>
        case -2: <error descr="Continue outside of enclosing switch expression">continue lab;</error>
        case -3: <error descr="Continue outside of enclosing switch expression">continue;</error>
        default: <error descr="Break outside of enclosing switch expression">break lab;</error>
      });
    }
  }
  
  enum Empty {}
  
  boolean testEmpty(Empty e) {
    return switch (<error descr="'switch' expression does not have any case clauses">e</error>) {};
  }
}