import java.util.Random;

class EnhancedSwitchStatements {
  static final int FI = 4;

  enum E { E1, E2 }

  void m(String... args) {
    String count;
    switch (args.length) {
      case 0 -> throw new IllegalStateException("no args");
      case 1 -> count = "one";
      default -> { count = "many"; }
    }

    switch (new Random().nextInt()) {
      case 0 -> throw new IllegalStateException("no args");
      <error descr="Statement must be prepended with a case label">break;</error>
    }
    switch (new Random().nextInt()) {
      case 0 -> throw new IllegalStateException("no args");
      <error descr="Different 'case' kinds used in 'switch'">case 1:</error> break;
    }
    switch (new Random().nextInt()) {
      case 0: throw new IllegalStateException("no args"); break;
      <error descr="Different 'case' kinds used in 'switch'">case 1</error> -> { System.out.println("one"); }
    }

    { <error descr="Case statement outside switch">case 11 -> System.out.println("hi there");</error> }
    { <error descr="Case statement outside switch">default -> System.out.println("hi there");</error> }

    switch (new Random().nextInt()) {
      case 42 -> <error descr="Not a statement">"bingo";</error>
    }

    switch (new Random().nextInt()) {
      <error descr="Duplicate default label">default</error> -> noop();
      case 1 -> noop();
      <error descr="Duplicate default label">default</error> -> noop();
    }

    switch (new Random().nextInt()) {
      case <error descr="Duplicate label '1'">FI/2 - 1</error> -> noop();
      case <error descr="Duplicate label '1'">(1 + 35/16) % 2</error> -> noop();
      case FI - 8 -> noop();
    }

    final byte b = 127;
    switch (new Random().nextInt()) {
      case <error descr="Duplicate label '127'">b</error> -> System.out.println("b=" + b + ";");
      case <error descr="Duplicate label '127'">127</error> -> System.out.println("sweet spot");
    }

    switch (0) {
      case 0 -> noop();
      case "\410" == "!0" ? 1 : 0 -> noop();
      case "" == "" + "" ? 3 : 0 -> noop();
    }

    switch (E.valueOf("E1")) {
      case E1 -> noop();
    }

    switch (E.valueOf("E1")) {
      case E1 -> noop();
      case E2 -> noop();
      case <error descr="Patterns in switch are not supported at language level '15'">null</error> -> noop();
    }

    switch (E.valueOf("E1")) {
      case <error descr="An enum switch case label must be the unqualified name of an enumeration constant">E.E1</error> -> noop();
      case E2 -> noop();
      case <error descr="Incompatible types. Found: 'int', required: 'EnhancedSwitchStatements.E'">1</error> -> noop();
    }

    switch (new Random().nextInt()) {
      case <error descr="Duplicate label '1'">1</error>, <error descr="Duplicate label '1'">1</error> -> noop();
    }

    switch (new Random().nextInt()) {
      case 1, <error descr="Duplicate label '2'">2</error>:
        noop(); break;
      case 3, <error descr="Duplicate label '2'">2</error>:
        noop(); break;
    }

    switch (<error descr="Selector type of 'java.lang.Object' is not supported at language level '15'">new Object()</error>) { }
  }

  private static void noop() { }
  
  void differentCase() {
    int a = 1;
    switch (a) {
      case 1:
        System.out.println(1);
        break;
      <error descr="Different 'case' kinds used in 'switch'">case 2</error> -> {
        System.out.println(2);
      }
    }
  }
}