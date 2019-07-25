class ValueBreaks {
  static final int ref = -1;

  void m() {
    l1: break l1;

    <error descr="Break outside switch or loop">break;</error>
    break <error descr="Undefined label: 'wtf'">wtf</error>;
    <error descr="Value break outside switch expression">break 42;</error>

    switch (0) {
      case 0: <error descr="Yield outside of switch expression">yield 42;</error>
      case 1: break <error descr="Undefined label: 'ref'">ref</error>;
      case 2: break <error descr="Undefined label: 'wtf'">wtf</error>;
      case 3: ref: break ref;
      case 4: ref: <error descr="Yield outside of switch expression">yield (ref);</error>
    };

    sink(switch (0) {
      case 0 -> { while (true) <error descr="Value break outside switch expression">break 42;</error> }
      case 1 -> { while (true) <error descr="Value break outside switch expression">break ref;</error> }
      case 2 -> { while (true) break <error descr="Undefined label: 'wtf'">wtf</error>; }
      case 3 -> { yield ref; }
      case 4 -> { yield (ref); }
      case 5 -> { break <error descr="Cannot resolve symbol 'wtf'">wtf</error>; }
      case 6 -> {
        int a = 0;
        a: switch (0) {
          default: break <error descr="Reference to 'a' is ambiguous, both 'a:' and 'variable a' match">a</error>;
        }
      }
      default -> throw new RuntimeException();
    });

    switch (0) {
      case 0 -> { while (true) break <error descr="Undefined label: 'ref'">ref</error>; }
      case 1 -> {
        int a = 0;
        a: switch (0) {
          default: break a;
        }
      }
    }

    ref: sink(switch (0) {
      default: break <error descr="Reference to 'ref' is ambiguous, both 'ref:' and 'ValueBreaks.ref' match">ref</error>;
    });

    while (true) {
      sink(switch (0) {
        default: yield<error descr="Expression expected">;</error>
      });
    }
  }

  private static void sink(Object o) { }
}