import java.util.*;

class Main {

  void dominance(Object o, Integer ii, String s, Day d) {
    // A switch label that has a pattern case label element p dominates another switch label that has a pattern case label element q if p dominates q
    switch (o) {
      case List n:
        System.out.println("num");
        break;
      case <error descr="Label is dominated by a preceding case label 'List n'">List i</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    String str;
    str = switch (o) {
      case List n -> "num";
      case <error descr="Label is dominated by a preceding case label 'List n'">List i</error> -> "int";
      default -> "def";
    };

    switch (o) {
      case Number n:
        System.out.println("num");
        break;
      case <error descr="Label is dominated by a preceding case label 'Number n'">Integer i</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Number n -> "num";
      case <error descr="Label is dominated by a preceding case label 'Number n'">Integer i</error> -> "int";
      default -> "def";
    };

    // Dominance permits a guarded pattern to be followed by its unguarded form:
    switch (o) {
      case Integer o1 && o1 != null:
        System.out.println("num");
        break;
      case Integer i:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Integer o1 && o1 != null -> "num";
      case Integer i -> "int";
      default -> "def";
    };

    switch (o) {
      case (Integer i):
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label '(Integer i)'">Integer o1 && o1 != null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Integer i) -> "num";
      case <error descr="Label is dominated by a preceding case label '(Integer i)'">Integer o1 && o1 != null</error> -> "int";
      default -> "def";
    };

    switch (o) {
      case (Integer o1 && o1 > 5):
        System.out.println("int");
        break;
      case Integer o2 && o2 != null:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Integer o1 && o1 > 5) -> "num";
      case Integer o2 && o2 != null -> "int";
      default -> "def";
    };

    switch (o) {
      case (Number i && false):
        System.out.println("int");
        break;
      case Integer o2 && o2 != null:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Number i && false) -> "num";
      case Integer o2 && o2 != null -> "int";
      default -> "def";
    };

    switch (o) {
      case (Integer i && true):
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label '(Integer i && true)'">(Integer o2 && o2 != null)</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Integer i && true) -> "num";
      case <error descr="Label is dominated by a preceding case label '(Integer i && true)'">(Integer o2 && o2 != null)</error> -> "int";
      default -> "def";
    };

    // A switch label that has a pattern case label element p that is total for the type of the selector expression
    // of the enclosing switch statement or switch expression dominates a switch label that has a null case label element.
    switch (ii) {
      case Object obj:
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label 'Object obj'">null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case Object obj -> "num";
      case <error descr="Label is dominated by a preceding case label 'Object obj'">null</error> -> "int";
      default -> "def";
    };

    switch (ii) {
      case Object obj, <error descr="Label is dominated by a preceding case label 'Object obj'">null</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case Object obj, <error descr="Label is dominated by a preceding case label 'Object obj'">null</error> -> "int";
      default -> "def";
    };

    switch (ii) {
      case (Integer i && true):
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label '(Integer i && true)'">null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case (Integer i && true) -> "int";
      case <error descr="Label is dominated by a preceding case label '(Integer i && true)'">null</error> -> "int";
      default -> "def";
    };

    switch (ii) {
      case ((Integer i && false)):
        System.out.println("int");
        break;
      case null:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case ((Integer i && false)) -> "int";
      case null -> "int";
      default -> "def";
    };

    // A switch label that has a pattern case label element p dominates another switch label that has a constant case label element c
    // if either of the following is true:
    //the type of c is a primitive type and its wrapper class is a subtype of the erasure of the type of p.
    //the type of c is a reference type and is a subtype of the erasure of the type of p.
    switch (ii) {
      case Integer i:
        break;
      case <error descr="Label is dominated by a preceding case label 'Integer i'">1</error>: case <error descr="Label is dominated by a preceding case label 'Integer i'">2</error>:
        break;
    }
    str = switch (s) {
      case String sss -> "s";
      case <error descr="Label is dominated by a preceding case label 'String sss'">"1"</error>, <error descr="Label is dominated by a preceding case label 'String sss'">"2"</error> -> "1";
    };

    // any type of pattern (including guarded patterns) dominates constant cases
    switch (d) {
      case Day dd: break;
      case <error descr="Label is dominated by a preceding case label 'Day dd'">MONDAY</error>: break;
    }
    str = switch (ii) {
      case (Integer in && in != null) -> "";
      case <error descr="Label is dominated by a preceding case label '(Integer in && in != null)'">1</error> -> "";
      case default -> "";
    };
    switch (d) {
      case (Day dd && true): break;
      case  <error descr="Label is dominated by a preceding case label '(Day dd && true)'">MONDAY</error>: break;
    }
  }
}

sealed interface I {
}

enum Day {
  MONDAY, TUESDAY, WEDNESDAY
}

final class Sub1 implements I {
}

final class Sub2 implements I {
}

sealed class Sub3 implements I {
}

final class Sub4 extends Sub3 {
}

final class Sub5 {
}

final class Sub6 extends Sub3 {
}

interface I2 {
}

class Sub7 implements I2 {
}

class Sub8 implements I2 {
}

sealed interface I3 {
}

final class Sub9 implements I3 {
}

sealed abstract class Sub10 implements I3 {
}

final class Sub11 extends Sub10 {
}

final class Sub12 extends Sub10 {
}

sealed interface AorBorC {}
sealed interface AorB extends AorBorC {}
sealed interface BorC extends AorBorC {}
sealed interface AorC extends AorBorC {}
final class A implements AorB, AorC {}
final class B implements AorB, BorC {}
final class C implements AorC, BorC {}
sealed interface J1 {}
sealed interface J2 extends J1 permits R1 {}
record R1() implements J1, J2 {}
record R2() implements J1 {}