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
      case String s, X x -> <error descr="Cannot resolve symbol 'x'">x</error>.f();
      default -> 1;
    };

    int i5 = switch(o) {
      case String s, X x -> <error descr="Cannot resolve symbol 'x'">x</error>.f();
      default -> 1;
    };

    int i6 = switch(o) {
      case X x, String s -> <error descr="Cannot resolve symbol 'x'">x</error>.f();
      default -> 1;
    };
    return i1 + i2 + i3 + i4 + i5 + i6;
  }

  void checkSwitchSelectorType(boolean b, double d, int[] array) {
    switch (<error descr="Selector type of 'boolean' is not supported">b</error>) {
      case true:
        System.out.println("true");
        break;
      case false:
        System.out.println("false");
        break;
    }
    String str;
    str = switch(<error descr="Selector type of 'double' is not supported">d</error>) {
      case 1 -> "ok";
      case 2 -> "not ok";
    };

    switch (array) {
      case int[] arr:
        System.out.println("true");
        break;
    }
    str = switch (array) {
      case int[] arr -> "true";
    };

    // intersection type
    var values = List.of("foo", 3, 4.0);
    for (var value : values) {
      switch (value) {
        case Integer i -> System.out.println("integer !");
        case String s -> System.out.println("string !");
        case Object o -> System.out.println("object !");
      }
    }
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
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">'1'</error>:
        System.out.println("ok");
    }
    str = switch (i) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">'1'</error> ->"ok";
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

  void patternsCompatibilty(I i, Object o, List<? extends Number> list1, List<Integer> list2) {
    switch (i) {
      case Sub1 s2:
        System.out.println("s1");
        break;
      case <error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error>:
        System.out.println("s5");
      case ((((<error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error>)) && Math.random() > 0.5)):
        System.out.println("");
      default:
        System.out.println("s");
    }
    String str;
    str = switch (i) {
      case Sub1 s2 -> "s1";
      case <error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error> -> "s5";
      case ((((<error descr="Incompatible types. Found: 'Sub5', required: 'I'">Sub5 s5</error>)) && Math.random() > 0.5)) -> "";
      default -> "s";
    };

    switch (i) {
      // total pattern
      case Object oo:
        System.out.println("s1");
    }
    str = switch (i) {
      // total pattern
      case Object oo -> "s1";
    };

    // unsafe casts
    switch (list1) {
      case <error descr="'java.util.List<capture<? extends java.lang.Number>>' cannot be safely cast to 'java.util.List<java.lang.Integer>'">List<Integer> l</error>:
        break;
      case ((((<error descr="'java.util.List<capture<? extends java.lang.Number>>' cannot be safely cast to 'java.util.List<java.lang.Integer>'">List<Integer> l</error>)) && Math.random() > 0.5)):
        break;
    }

    switch (list1.get(0)) {
      case null: {}
      default: {}
    }

    switch (new int[0]) {
      case null: {}
      default: {}
    }
    

    switch (list2) {
      case List<? extends Number> l:
        break;
    }
    switch (o) {
      case <error descr="'java.lang.Object' cannot be safely cast to 'java.util.List<java.lang.Integer>'">List<Integer> ll</error>:
        break;
      case ((((<error descr="'java.lang.Object' cannot be safely cast to 'java.util.List<java.lang.Integer>'">List<Integer> ll</error>)) && Math.random() > 0.5)):
        break;
      case default:
        break;
    }
    switch (list1) {
      case Object oo:
        break;
    }

    // null selector
    switch (null) {
      case null:
        break;
      default:
        break;
    };
    str = switch (null) {
      case null -> "null";
      default -> "def";
    };

    switch (o) {
      case <error descr="Unexpected type. Found: 'int', required: 'class or array'">int ii</error>: break;
      case ((((<error descr="Unexpected type. Found: 'long', required: 'class or array'">long l</error>)) && Math.random() > 0.5)): break;
      default: break;
    }
    str = switch (o) {
      case <error descr="Unexpected type. Found: 'int', required: 'class or array'">int ii</error> -> "";
      case ((((<error descr="Unexpected type. Found: 'long', required: 'class or array'">long l</error>)) && Math.random() > 0.5)) -> "";
      default -> "";
    };
  }

  private static final int constant = 1;
  void duplicateLabels(Integer i) {
    String str;
    switch (i) {
      case <error descr="Duplicate label '1'">1</error>:
        break;
      case <error descr="Duplicate label '1'">Main.constant</error>:
        break;
    }
    str = switch (i) {
      case <error descr="Duplicate label '1'">1</error> -> "";
      case <error descr="Duplicate label '1'">constant</error> -> "";
    };

    // A switch label may not use more than one default label
    switch (i) {
      case 1, <error descr="Duplicate default label">default</error>:
        System.out.println("s1");
        break;
      <error descr="Duplicate default label">default</error>:
        System.out.println("s");
    }
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

    // total pattern duplicates
    switch (i) {
      case <error descr="Duplicate total pattern">Object o</error>:
        break;
      case <error descr="Duplicate total pattern">((Integer ii && true))</error>:
        break;
    }
    str = switch (i) {
      case ((Integer ii && false)) -> "";
      case Number n -> "";
    };
  }

  void fallThroughToPatterns(Object o, Integer ii) {
    // If a switch label has a null case label element then if the switch label also has any pattern case element labels, t
    // they must be type patterns (14.30.1).
    switch (o) {
      case Integer i && i != null, <error descr="Illegal fall-through from a pattern">null</error>:
        break;
      case default:
        break;
    }
    String str;
    str = switch (o) {
      case Integer i && i != null, <error descr="Illegal fall-through from a pattern">null</error> -> "s";
      default -> "null";
    };

    switch (o) {
      case null, <error descr="Illegal fall-through to a pattern">Integer i && i != null</error>:
        break;
      case default:
        break;
    }
    str = switch (o) {
      case null, <error descr="Illegal fall-through to a pattern">Integer i && i != null</error> -> "s";
      default -> "null";
    };
    switch (o) {
      case null: case <error descr="Illegal fall-through to a pattern">Integer i && i != null</error>:
        break;
      case default:
        break;
    }
    str = switch (o) {
      case null: case <error descr="Illegal fall-through to a pattern">Object i && i != null</error>: yield "sfds";
    };
    str = switch (o) {
      case null, <error descr="Illegal fall-through to a pattern">Object i && i != null</error> -> "sfds";
      case default -> "fsd";
    };
    switch (o) {
      case null: case Integer i:
        break;
      case default:
        break;
    }
    str = switch (o) {
      case null: case Integer i: yield "s";
      case default: yield "d";
    };

    // A switch label may not have more than one pattern case label element.
    switch (o) {
      case Integer i, <error descr="Illegal fall-through to a pattern">Long l && l != null</error>: System.out.println("s");
      default: System.out.println("null");
    }
    str = switch (o) {
      case Integer i, <error descr="Illegal fall-through to a pattern">Long l && l != null</error> -> "s";
      default -> "null";
    };
    switch (o) {
      case Integer i: case <error descr="Illegal fall-through to a pattern">Long l</error>: System.out.println("s");
      default: System.out.println("null");
    }
    str = switch (o) {
      case Integer i: case <error descr="Illegal fall-through to a pattern">Long l</error>: yield "s";
      default: yield "res";
    };
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
    switch (o) {
      case Integer i: case <error descr="Illegal fall-through from a pattern">default</error>: System.out.println("s");
    }
    str = switch (o) {
      case Integer i: case <error descr="Illegal fall-through from a pattern">default</error>: yield "s";
    };
    switch (ii) {
      case Integer i && i > 1:
      <error descr="Illegal fall-through from a pattern">default</error>: System.out.println("null");
    }
    str = switch (ii) {
      case Integer i && i > 1:
      <error descr="Illegal fall-through from a pattern">default</error>: yield "null";
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
    switch (ii) {
      case 1, 2: case null, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>:
        System.out.println("s1");
        break;
      default: System.out.println("null");
    }
    // more complex case
    switch (ii) {
      case 1, null, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>, <error descr="Illegal fall-through from a pattern">default</error>:
        System.out.println("s1");
        break;
    }
    str = switch (ii) {
      case 1, null, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>, <error descr="Illegal fall-through from a pattern">default</error> -> "s1";
    };
    str = switch (ii) {
      case 1, 2, <error descr="Illegal fall-through to a pattern">Integer i1 && i1 > 5</error>: case <error descr="Illegal fall-through from a pattern">null</error>:
        System.out.println("s1");
        yield "s1";
      default: yield "def";
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
    // switch expressions
    str = switch (o) {
      case null, Integer i:
        if (o != null) {
          throw new IllegalArgumentException("");
        }
      case <error descr="Illegal fall-through to a pattern">Float d</error>:
        System.out.println("float");
      default:
        yield "1";
    };
    str = switch (o) {
      case null:
        if (o != null) {
          throw new IllegalArgumentException("");
        }
        yield "1";
      case Float d:
        System.out.println("float");
      default:
        yield "1";
    };
  }

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

    // !!! here are some contradictory examples with spec, but javac still compiles them. To be discussed in the mailing list
    // at least for now it's look quite logical if we have a total pattern in a switch label, and following constant switch label,
    // then the first switch label dominates the second one.
    switch (d) {
      case Day dd: break;
      case <error descr="Label is dominated by a preceding case label 'Day dd'">MONDAY</error>: break;
    }
    str = switch (ii) {
      case Integer in && in != null -> "";
      case 1 -> "";
      case default -> "";
    };
    switch (d) {
      case (Day dd && true): break;
      case  <error descr="Label is dominated by a preceding case label '(Day dd && true)'">MONDAY</error>: break;
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
      case Day dd && dd != null:
        System.out.println("ok");
      case MONDAY:
        System.out.println("mon");
    };
    switch (<error descr="'switch' statement does not cover all possible input values">d</error>) {
      case Day dd && dd != null:
        System.out.println("ok");
    };

    str = switch (<error descr="'switch' expression does not cover all possible input values">d</error>) {
      case MONDAY, TUESDAY -> System.out.println("ok");
    };
    str = switch (d) {
      case MONDAY, TUESDAY, WEDNESDAY -> "ok";
    };
    str = switch (d) {
      case MONDAY, TUESDAY, default -> "ok";
    };

    switch (d) {
      case <error descr="'switch' has both a total pattern and a default label">((Day dd && true))</error>:
        System.out.println("ok");
      <error descr="'switch' has both a total pattern and a default label"><caret>default</error>: // blah blah blah
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
      case Sub3 s3 && true -> "ok";
    };

    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Sub1 s1:
        System.out.println("ok");
        break;
      case Sub2 s2:
        System.out.println("ok");
        break;
    }
    str = switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
    };
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
    str = switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case Sub1 s1 -> "ok";
      case Sub2 s2 -> "ok";
      case Sub4 s4 -> "ok";
      case Sub6 s6 -> "ok";
    };

    switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
      case Sub1 s1:
        break;
      case (Sub2 s2 && false):
        break;
      case Sub3 s3:
        break;
    }
    str = switch(<error descr="'switch' expression does not cover all possible input values">i</error>) {
      case I in && in != null -> "ok";
    };

    switch (i3) {
      case (Sub9 s && true):
        break;
      case Sub11 s:
        break;
      case Sub12 s && true:
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

    switch (<error descr="'switch' statement does not cover all possible input values">ii</error>) {
      case BB b -> {}
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

sealed interface II<T> {}
final class AA implements II<String> {}
final class BB<T> implements II<Object> {}