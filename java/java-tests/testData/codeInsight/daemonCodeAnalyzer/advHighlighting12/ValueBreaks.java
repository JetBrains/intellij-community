class ValueBreaks {
  static final int ref = -1;

  void m() {
    l1: break l1;

    <error descr="Break outside switch or loop">break;</error>
    break <error descr="Undefined label: 'wtf'">wtf</error>;
    <error descr="Value break outside switch expression">break 42;</error>

    switch (0) {
      case 0: <error descr="Value break outside switch expression">break 42;</error>
      case 1: break <error descr="Undefined label: 'ref'">ref</error>;
      case 2: break <error descr="Undefined label: 'wtf'">wtf</error>;
      case 3: ref: break ref;
    };

    sink(switch (0) {
      case 0 -> { while (true) <error descr="Value break outside switch expression">break 42;</error> }
      case 1 -> { while (true) break <error descr="Undefined label: 'ref'">ref</error>; }
      case 2 -> { while (true) break <error descr="Undefined label: 'wtf'">wtf</error>; }
      case 3 -> { break ref; }
      case 4 -> { break <error descr="Cannot resolve symbol 'wtf'">wtf</error>; }
      default -> throw new RuntimeException();
    });

    ref: sink(switch (0) {
      default: break <error descr="Reference to 'ref' is ambiguous, both 'ref:' and 'ValueBreaks.ref' match">ref</error>;
    });

    while (true) {
      sink(switch (0) {
        default: <error descr="Missing break value">break;</error>
      });
    }
  }

  private static void sink(Object o) { }
}