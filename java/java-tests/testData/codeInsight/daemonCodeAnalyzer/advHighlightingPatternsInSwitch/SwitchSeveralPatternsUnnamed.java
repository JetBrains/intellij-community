class Test {
  enum X{A, B}
  
  record R(int x, int y) {}
  record R1(int z) {}

  void test(Object obj) {
    switch (obj) {
      case Integer _, String _ -> System.out.println("string or int");
      case R(_, _), R1 _ -> System.out.println("R or R1");
      default -> System.out.println("other");
    }
  }

  void testRepeat(Object obj) {
    switch (obj) {
      case Integer _, String _ -> System.out.println("string or int");
      case Double _, <error descr="Label is dominated by a preceding case label 'Integer _'">Integer _</error> -> System.out.println("double or int");
      default -> System.out.println("other");
    }
  }

  void testEnum(Object obj) {
    switch (obj) {
      case X.A, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a list of case patterns">String _</error> -> System.out.println("string or int");
      case Integer _, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a list of case patterns">X.B</error> -> System.out.println("string or int");
      default -> System.out.println("other");
    }
  }

  void test2(Object obj) {
    switch (obj) {
      case <error descr="Invalid case label combination: multiple patterns are allowed only if none of them declare any pattern variables">Integer x</error>, String _ -> System.out.println("string or int");
      case <error descr="Invalid case label combination: multiple patterns are allowed only if none of them declare any pattern variables">R(_, var i)</error>, R1 _ -> System.out.println("R or R1");
      default -> System.out.println("other");
    }
  }
  
  void testGuards(Object obj) {
    switch (obj) {
      case Integer _ when ((Integer)obj) > 0<error descr="':' or '->' expected"><error descr="Unexpected token">,</error></error>
           <error descr="Unnamed variable declaration must have an initializer">String _</error><error descr="';' expected"> </error><error descr="Cannot resolve symbol 'when'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">when</error> <error descr="Not a statement">!((String)obj).isEmpty()</error> <error descr="Unexpected token">-></error> System.out.println("Positive integer or non-empty string");
      default -> System.out.println("other");
    }
  }

  void testFallthrough(Object obj) {
    switch (obj) {
      case Integer _:
      case String _:
        System.out.println("Number or string");
        break;
      case Double _:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Float f</error>:
        System.out.println("double or float");
        break;
      default:
        System.out.println("other");
    }
  }

  record RR1() {}
  record RR2() {}

  void testNoVars(Object obj) {
    switch(obj) {
      case RR1(), RR2() -> {}
      default -> {}
    }
  }
}
