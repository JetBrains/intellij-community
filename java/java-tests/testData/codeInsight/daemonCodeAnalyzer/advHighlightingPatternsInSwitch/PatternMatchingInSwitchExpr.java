import java.util.*;

class Main {
  static class X {
    int f() { return 0; }
  }

  int switchTestResolve(Object o) {
    int i1 = switch(o) {
      case X x -> x.f();
      default -> 1;
    };
    int i2 = switch(o) {
      case null, X x -> x.f();
      default -> 1;
    };

    int i3 = switch(o) {
      case X x, null -> x.f();
      default -> 1;
    };

    int i4 = switch(o) {
      case String s, <error descr="Illegal fall-through to a pattern">X x</error> -> x.f();
      default -> 1;
    };

    int i5 = switch(o) {
      case String s, <error descr="Illegal fall-through to a pattern">X x</error> -> x.f();
      default -> 1;
    };

    int i6 = switch(o) {
      case X x, <error descr="Illegal fall-through to a pattern">String s</error> -> x.f();
      default -> 1;
    };
    return i1 + i2 + i3 + i4 + i5 + i6;
  }

  void constLabelAndSelectorCompatibility(Number n, CharSequence c, Integer i, String s) {
    switch (n) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Number'">1</error>:
        System.out.println("ok");
    }
    String str;
    str = switch (n) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Number'">1</error> -> "ok";
      default -> "not ok";
    };

    switch (c) {
      case <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.CharSequence'">"ok"</error>:
        System.out.println("ok");
    }
    str = switch (c) {
      case <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.CharSequence'">"ok"</error> -> "ok";
      default -> "not ok";
    };

    switch (i) {
      case 1:
        System.out.println("ok");
    }
    str= switch (i) {
      case 1 -> "ok";
      default -> "not ok";
    };

    switch (s) {
      case "null" :
        System.out.println("null");
        break;
      default:
        System.out.println("s");
    }
    str = switch (s) {
      case "null" -> "null";
      default -> "s";
    };
  }

  void incompatibleNullLabelAndSelector(int i) {
    switch (i) {
      case <error descr="'null' cannot be converted to 'int'">null</error>:
        System.out.println("ok");
    }
    String str;
    str = switch (i) {
      case <error descr="'null' cannot be converted to 'int'">null</error> -> "ok";
        default -> "not ok";
    };
  }

  void defaultAlwaysCompatible(int i) {
    switch (i) {
      case 1, default:
        System.out.println("ok");
    }
    String str;
    str = switch (i) {
      case 1, default -> "ok";
    };
  }

  void patternsCompatibilty(I i) {
    switch (i) {
      case Sub1 s2:
        System.out.println("s1");
        break;
      case <error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error>:
        System.out.println("s5");
      default:
        System.out.println("s");
    }
    String str;
    str = switch (i) {
      case Sub1 s2 -> "s1";
      case <error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error> -> "s5";
      default -> "s";
    };

    switch (i) {
      // total pattern
      case Object o:
        System.out.println("s1");
    }
    str = switch (i) {
      // total pattern
      case Object o -> "s1";
    };
  }

  void duplicateLabels(Integer i) {
    // A switch label may not use more than one default label
    switch (i) {
      case 1, <error descr="Duplicate default label">default</error>:
        System.out.println("s1");
        break;
      <error descr="Duplicate default label">default</error>:
        System.out.println("s");
    }
    String str;
    str = switch (i) {
      case 1, <error descr="Duplicate default label">default</error> -> "s1";
      <error descr="Duplicate default label">default</error> -> "s";
    };

    switch (i) {
      case <error descr="Duplicate default label">default</error>:
        System.out.println("s1");
        break;
      case <error descr="Duplicate default label">default</error>:
        System.out.println("s");
    }
    str = switch (i) {
      case <error descr="Duplicate default label">default</error> -> "s1";
      case <error descr="Duplicate default label">default</error> -> "s";
    };

    // A switch label may not have more than one default case label element
    switch (i) {
      case <error descr="Duplicate default label">default</error>, <error descr="Duplicate default label">default</error>:
        System.out.println("s");
    }
    str = switch (i) {
      case <error descr="Duplicate default label">default</error>, <error descr="Duplicate default label">default</error> -> "s";
    };

    // A switch label may not have more than one null case label element.
    switch (i) {
      case 1, <error descr="Duplicate label 'null'">null</error>:
        System.out.println("s");
      case <error descr="Duplicate label 'null'">null</error>:
        System.out.println("null");
    }
    str = switch (i) {
      case 1, <error descr="Duplicate label 'null'">null</error> -> "s";
      case <error descr="Duplicate label 'null'">null</error> -> "null";
    };
  }

  void fallThroughToPatterns(Object o, Integer ii) {
    /* wasn't implemented in javac
      If a switch label has a null case label element then if the switch label also has any pattern case element labels, t
      they must be type patterns (14.30.1).
     */
    // A switch label may not have more than one pattern case label element.
    switch (o) {
      case Integer i, <error descr="Illegal fall-through to a pattern">Long l && l != null</error>: System.out.println("s");
      default: System.out.println("null");
    }
    String str;
    str = switch (o) {
      case Integer i, <error descr="Illegal fall-through to a pattern">Long l && l != null</error> -> "s";
      default -> "null";
    };
    // todo A switch label may not have both a pattern case label element and a default case label element.
    // A switch label may not have both a pattern case label element and a default case label element.
    switch (o) {
      case Integer i, <error descr="Illegal fall-through from a pattern">default</error>: System.out.println("s");
    }
    str = switch (o) {
      case Integer i, <error descr="Illegal fall-through from a pattern">default</error> -> "s";
    };
    switch (o) {
      case default, <error descr="Illegal fall-through to a pattern">Integer i</error>: System.out.println("s");
    }
    str = switch (o) {
      case default, <error descr="Illegal fall-through to a pattern">Integer i</error> -> "s";
    };

    // If a switch label has a constant case label element then if the switch label also has other case element labels
    // they must be either a constant case label element, the default case label element, or the null case label element.
    switch (ii) {
      case 1, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>:
        System.out.println("s1");
        break;
      default: System.out.println("null");
    }
    str = switch (ii) {
      case 1, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error> -> "s1";
      default -> "null";
    };
    switch (ii) {
      case Integer i1 && i1 > 5, <error descr="Illegal fall-through from a pattern">1</error>:
        System.out.println("s1");
        break;
      default: System.out.println("null");
    }
    str = switch (ii) {
      case Integer i1 && i1 > 5, <error descr="Illegal fall-through from a pattern">1</error> -> "s1";
      default -> "null";
    };
    // more complex case
    switch (ii) {
      case 1, null, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>, <error descr="Illegal fall-through from a pattern">default</error>:
        System.out.println("s1");
        break;
    }
    str = switch (ii) {
      case 1, null, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>, <error descr="Illegal fall-through from a pattern">default</error> -> "s1";
    };

    /**
     * It is a compile-time error if there is a statement in a switch block that consists of switch-labeled statement groups
     * for which both of the following are true:
     * It is labeled with a switch label that has a pattern case label element whose pattern introduces a pattern variable.
     * There is a statement preceding it in the switch block and that statement can completely normally (14.22).
     */
    switch (o) {
      case default:
        System.out.println("def");
      case <error descr="Illegal fall-through to a pattern">Float d</error>:
        System.out.println("float");
    }
    switch (o) {
      case null, Integer i:
        if (o != null) {
          throw new IllegalArgumentException("");
        }
      case <error descr="Illegal fall-through to a pattern">Float d</error>:
        System.out.println("float");
    }
    switch (o) {
      case null:
        if (o != null) {
          throw new IllegalArgumentException("");
        }
        break;
      case Float d:
        System.out.println("float");
      default:
        System.out.println("ok");
    }
  }

  void dominance(Object o, Integer ii) {
    // A switch label that has a pattern case label element p dominates another switch label that has a pattern case label element q if p dominates q
    switch (o) {
      case List n:
        System.out.println("num");
        break;
      case <error descr="This case label is dominated by a preceding case label">List i</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    String str;
    str = switch (o) {
      case List n -> "num";
      case <error descr="This case label is dominated by a preceding case label">List i</error> -> "int";
      default -> "def";
    };

    switch (o) {
      case Number n:
        System.out.println("num");
        break;
      case <error descr="This case label is dominated by a preceding case label">Integer i</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case Number n -> "num";
      case <error descr="This case label is dominated by a preceding case label">Integer i</error> -> "int";
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
      case <error descr="This case label is dominated by a preceding case label">Integer o1 && o1 != null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Integer i) -> "num";
      case <error descr="This case label is dominated by a preceding case label">Integer o1 && o1 != null</error> -> "int";
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
      case <error descr="This case label is dominated by a preceding case label">(Integer o2 && o2 != null)</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (o) {
      case (Integer i && true) -> "num";
      case <error descr="This case label is dominated by a preceding case label">(Integer o2 && o2 != null)</error> -> "int";
      default -> "def";
    };

    // A switch label that has a pattern case label element p that is total for the type of the selector expression
    // of the enclosing switch statement or switch expression dominates a switch label that has a null case label element.
    switch (ii) {
      case Object obj:
        System.out.println("int");
        break;
      case <error descr="This case label is dominated by a preceding case label">null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case Object obj -> "num";
      case <error descr="This case label is dominated by a preceding case label">null</error> -> "int";
      default -> "def";
    };

    switch (ii) {
      case Object obj, <error descr="This case label is dominated by a preceding case label">null</error>:
        System.out.println("int");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case Object obj, <error descr="This case label is dominated by a preceding case label">null</error> -> "int";
      default -> "def";
    };

    switch (ii) {
      case (Integer i && true):
        System.out.println("int");
        break;
      case <error descr="This case label is dominated by a preceding case label">null</error>:
        System.out.println("num");
        break;
      default:
        System.out.println("def");
        break;
    }
    str = switch (ii) {
      case (Integer i && true) -> "int";
      case <error descr="This case label is dominated by a preceding case label">null</error> -> "int";
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
  }

  void completeness(Day d, I i, I2 i2) {
    // old style switch, no completeness check
    switch (d) {
      case MONDAY, TUESDAY -> System.out.println("ok");
    }

    // If the type of the selector expression is an enum type E
    String str;
    <error descr="The switch statement does not cover all possible input values">switch</error> (d) {
      case Day dd && dd != null:
        System.out.println("ok");
      case MONDAY:
        System.out.println("mon");
    };

    str = <error descr="The switch expression does not cover all possible input values">switch</error> (d) {
      case MONDAY, TUESDAY -> System.out.println("ok");
    };
    str = switch (d) {
      case MONDAY, TUESDAY, WEDNESDAY -> "ok";
    };
    str = switch (d) {
      case MONDAY, TUESDAY, default -> "ok";
    };

    switch (d) {
      case <error descr="Switch has both a total pattern and a default label">((Day dd && true))</error>:
        System.out.println("ok");
      <error descr="Switch has both a total pattern and a default label">default:</error>
        System.out.println("mon");
    };
    switch (d) {
      case ((Day dd && dd != null)):
        System.out.println("ok");
      default:
        System.out.println("mon");
    };

    // If the type of the selector expression, T, names a sealed interface or a sealed class that is abstract
    switch(i) {
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
    str = switch(i) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
      case Sub3 s3 -> "ok";
    };

    <error descr="The switch statement does not cover all possible input values">switch</error> (i) {
      case Sub1 s1:
        System.out.println("ok");
        break;
      case Sub2 s2:
        System.out.println("ok");
        break;
    }
    str = <error descr="The switch expression does not cover all possible input values">switch</error>(i) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
    };
    switch (i) {
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
    str = switch(i) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
      case Sub4 s4 -> "ok";
      case Sub6 s6 -> "ok";
    };

    // If the type of the selector expression, T, is not an enum type and also does not name a sealed interface or a sealed class that is abstract
    <error descr="The switch statement does not cover all possible input values">switch</error> (i2) {
      case Sub7 s1:
        System.out.println("ok");
        break;
      case Sub8 s2:
        System.out.println("ok");
        break;
    }
    str = <error descr="The switch expression does not cover all possible input values">switch</error> (i2) {
      case Sub7 s1 -> "ok";
      case Sub8 s2 -> "ok";
    };
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