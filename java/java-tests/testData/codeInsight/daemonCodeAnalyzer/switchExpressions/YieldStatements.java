class YieldStatements {
  static final int ref = -1;

  void m(int i) {
    <error descr="Case statement outside switch">default:</error> <error descr="Yield outside of switch expression">yield 0;</error>

    l1: yield <error descr="Cannot resolve symbol 'l1'">l1</error>;

    switch (i) {
      default: <error descr="Yield outside of switch expression">yield 0;</error>
    }

    out: System.out.println(switch (i) {
      case 1 -> { while (true) yield ref; }
      case 2 -> { while (true) break <error descr="Undefined label: 'wtf'">wtf</error>; }
      case 3 -> { yield ref; }
      case 4 -> { <error descr="Illegal reference to restricted type 'yield'">yield</error> (ref); }
      case 5 -> { break <error descr="Undefined label: 'wtf'">wtf</error>; }
      case 6 -> {
        int a = 0;
        a: switch (0) { default: yield a; }
      }
      case 7 -> {
        Runnable r = () -> { <error descr="Yield outside of switch expression">yield 0;</error> };
        r.run();
        yield 0;
      }
      case 8 -> {
        switch (i) { default -> { yield 0; } }
      }
      case 9 -> { yield<error descr="Expression expected">;</error> }
      case 10 -> {
        int yield = i;
        yield yield;
      }
      case 11 -> { yield <error descr="Expression type should not be 'void'">m(0)</error>; }
      case 12 -> {
        switch (i) { default: <error descr="Break out of switch expression is not allowed">break out;</error>; }
      }
      default -> throw new RuntimeException();
    });

    out: while (true) {
      System.out.println(switch (i) {
        case 0: <error descr="Break out of switch expression is not allowed">break;</error>
        default: <error descr="Break out of switch expression is not allowed">break out;</error>
      });
    }
  }
  
  class <error descr="'yield' is a restricted identifier and cannot be used for type declarations">yield</error> {
    void test(<error descr="Illegal reference to restricted type 'yield'">yield</error> yield) {
      
    }
  }
  
}