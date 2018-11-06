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
      <error descr="Different case kinds used in the switch">break;</error>
    }
    switch (new Random().nextInt()) {
      case 0 -> throw new IllegalStateException("no args");
      <error descr="Different case kinds used in the switch">case 1:</error> break;
    }
    switch (new Random().nextInt()) {
      case 0: throw new IllegalStateException("no args"); break;
      <error descr="Different case kinds used in the switch">case 1 -> { System.out.println("one"); }</error>
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
      case <error descr="Constant expression required">null</error> -> noop();
    }
  }

  private static void noop() { }
}