class YieldStatements {
  void m(int i) {
    System.out.println(switch (i) {
      case 0: yield "zero";
      default: <error descr="Value breaks are superseded by 'yield' statements">break "many";</error>
    });

    switch (i) {
      default: <error descr="Yield outside of switch expression">yield 0;</error>
    }
    System.out.println(switch (i) {
      default:
        Runnable r = () -> { <error descr="Yield outside of switch expression">yield 0;</error> };
        r.run();
        yield 0;
    });
    System.out.println(switch (i) {
      default: switch (i) {
        default: yield "0";
      }
    });

    out: while (true) {
      System.out.println(switch (i) {
        case 0: <error descr="Break outside of enclosing switch expression">break;</error>
        case 1: <error descr="Value breaks are superseded by 'yield' statements">break i;</error>
        default: <error descr="Break outside of enclosing switch expression">break out;</error>
      });
    }

    System.out.println(switch (i) {
      default: yield <error descr="Expression type should not be 'void'">m(0)</error>;
    });

    int yield = i;
    i = switch (i) {
      default: yield yield;
    };
  }
}