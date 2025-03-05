import java.util.List;

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
      case Integer o1 when o1 != null:
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
      case Integer o1 when o1 != null -> "num";
      case Integer i -> "int";
      default -> "def";
    };

    switch (o) {
      case Integer i:
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label 'Integer i'">Integer o1</error> when o1 != null: 
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Integer i -> "num";
      case <error descr="Label is dominated by a preceding case label 'Integer i'">Integer o1</error> when o1 != null -> "int"; 
        default -> "def";
    };

    switch (o) {
      case Integer o1 when o1 > 5:
        System.out.println("int");
        break;
      case Integer o2 when o2 != null:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Integer o1 when o1 > 5 -> "num";
      case Integer o2 when o2 != null -> "int";
      default -> "def";
    };

    switch (o) {
      case Number i when Math.random() > 0.5:
        System.out.println("int");
        break;
      case Integer o2 when o2 != null:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Number i when Math.random() > 0.5 -> "num";
      case Integer o2 when o2 != null -> "int";
      default -> "def";
    };

    switch (o) {
      case Integer i when true:
        System.out.println("int");
        break;
      case <error descr="Label is dominated by a preceding case label 'Integer i'">Integer o2</error> when o2 != null: 
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Integer i when true -> "num";
      case <error descr="Label is dominated by a preceding case label 'Integer i'">Integer o2</error> when o2 != null -> "int"; 
        default -> "def";
    };

    // A switch label that has a pattern case label element p that is total for the type of the selector expression
    // of the enclosing switch statement or switch expression dominates a switch label that has a null case label element.
    switch (ii) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Object obj</error>: 
        System.out.println("int");
        break;
      case null:
        System.out.println("num");
        break;
      <error descr="'switch' has both an unconditional pattern and a default label">default</error>: 
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Object obj</error> -> "num"; 
        case null -> "int";
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> "def"; 
    };

    switch (ii) {
      case Object obj, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>: 
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Object obj</error> -> "int";
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> "def"; 
    };

    switch (ii) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Integer i</error> when true:
        System.out.println("int");
        break;
      case null:
        System.out.println("num");
        break;
      <error descr="'switch' has both an unconditional pattern and a default label">default</error>:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Integer i</error> when true -> "int"; 
        case null -> "int";
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> "def"; 
    };

    switch (ii) {
      case Integer i when Math.random() > 0.5:
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
      case Integer i when Math.random() > 0.5 -> "int";
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
      case <error descr="Label is dominated by a preceding case label 'Integer i'">1</error>: 
      case <error descr="Label is dominated by a preceding case label 'Integer i'">2</error>: 
        break;
    }
    str = switch (s) {
      case String sss -> "s";
      case <error descr="Label is dominated by a preceding case label 'String sss'">"1"</error>, <error descr="Label is dominated by a preceding case label 'String sss'">"2"</error> -> "1"; 
    };

    // any type of pattern (including guarded patterns) dominates constant cases
    switch (d) {
      case Day dd:
        break;
      case <error descr="Label is dominated by a preceding case label 'Day dd'">MONDAY</error>:
        break; 
    }
    str = switch (ii) {
      case Integer in when in != null -> "";
      case 1 -> "";
      default -> "";
    };
    switch (d) {
      case Day dd when true:
        break;
      case <error descr="Label is dominated by a preceding case label 'Day dd'">MONDAY</error>:
        break;
    }
  }

  void completeness(Day d, I i, I2 i2, I3 i3, AorBorC abc, J1 j, II<Integer> ii) {
    // old style switch, no completeness check
    switch (d) {
      case MONDAY, TUESDAY -> System.out.println("ok");
    }

    // If the type of the selector expression is an enum type E
    String str;
    switch (<error descr="'switch' statement does not cover all possible input values">d</error>) {
      case Day dd when dd != null:
        System.out.println("ok");
      case MONDAY:
        System.out.println("mon");
    }
    switch (<error descr="'switch' statement does not cover all possible input values">d</error>) {
      case Day dd when dd != null:
        System.out.println("ok");
    }

    str = switch (d) {
      case MONDAY, TUESDAY -> <error descr="Bad type in switch expression: void cannot be converted to java.lang.String">System.out.println("ok")</error>;
    };
    str = switch (d) {
      case MONDAY, TUESDAY, WEDNESDAY -> "ok";
    };
    str = switch (<error descr="'switch' expression does not cover all possible input values">d</error>) { 
      case MONDAY, TUESDAY -> "ok";
    };

    switch (d) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Day dd</error> when true:
        System.out.println("ok");
      <error descr="'switch' has both an unconditional pattern and a default label">default</error>: // blah blah blah 
        System.out.println("mon");
    }
    ;
    switch (d) {
      case Day dd when dd != null:
        System.out.println("ok");
      default:
        System.out.println("mon");
    }
    ;

    // If the type of the selector expression, T, names a sealed interface or a sealed class that is abstract
    switch (i) {
      case Sub1 s1:
        System.out.println("ok");
        break;
      case Sub2 s2:
        System.out.println("ok");
        break;
      case Sub3 s3:
        System.out.println("ok");
        break;
    }
    str = switch (i) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
      case Sub3 s3 when true -> "ok";
    };

    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Sub1 s1:
        System.out.println("ok");
        break;
      case Sub2 s2:
        System.out.println("ok");
        break;
    }
    str = switch (<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
    }
    ;
    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Sub1 s1:
        System.out.println("ok");
        break;
      case Sub2 s2:
        System.out.println("ok");
        break;
      case Sub4 s4:
        System.out.println("ok");
        break;
      case Sub6 s6:
        System.out.println("ok");
        break;
    }
    str = switch (<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
      case Sub4 s4 -> "ok";
      case Sub6 s6 -> "ok";
    };

    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Sub1 s1:
        break;
      case Sub2 s2 when Math.random() > 0.5:
        break;
      case Sub3 s3:
        break;
    }
    str = switch (<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case I in when in != null -> "ok";
    };

    switch (i3) {
      case Sub9 s when true:
        break;
      case Sub11 s:
        break;
      case Sub12 s when true:
        break;
    }

    str = switch (abc) {
      case A a -> "1";
      case B b -> "2";
      case C c -> "3";
    };
    str = switch (abc) {
      case A a -> "1";
      case C c -> "2";
      case AorB ab -> "3";
      case BorC bc -> "4";
    };

    switch (j) {
      case R1 r1:
        break;
      case R2 r2:
        break;
    }

    // If the type of the selector expression, T, is not an enum type and also does not name a sealed interface or a sealed class that is abstract
    switch (<error descr="'switch' statement does not cover all possible input values">i2</error>) {
      case Sub7 s1:
        System.out.println("ok");
        break;
      case Sub8 s2:
        System.out.println("ok");
        break;
    }
    str = switch (<error descr="'switch' expression does not cover all possible input values">i2</error>) { 
      case Sub7 s1 -> "ok";
      case Sub8 s2 -> "ok";
    };

    // empty switches
    switch (d) {
    }
    str = switch (<error descr="'switch' expression does not have any case clauses">d</error>) {
    };

    switch (<error descr="'switch' statement does not have any case clauses">i</error>) { 
    }
    str = switch (<error descr="'switch' expression does not have any case clauses">i</error>) {
    };

    switch (<error descr="'switch' statement does not have any case clauses">i2</error>) { 
    }
    str = switch (<error descr="'switch' expression does not have any case clauses">i2</error>) { 
    };

    // 'case AA' is redundant brach here as 'AA' is not castable to II<Integer>, so the code compiles correctly
    switch (ii) {
      case BB b -> {
      }
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

sealed interface AorBorC {
}

sealed interface AorB extends AorBorC {
}

sealed interface BorC extends AorBorC {
}

sealed interface AorC extends AorBorC {
}

final class A implements AorB, AorC {
}

final class B implements AorB, BorC {
}

final class C implements AorC, BorC {
}

sealed interface J1 {
}

sealed interface J2 extends J1 permits R1 {
}

record R1() implements J1, J2 {
}

record R2() implements J1 {
}

sealed interface II<T> {
}

final class AA implements II<String> {
}

final class BB<T> implements II<Object> {
}