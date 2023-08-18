public class Main {
  record R() {}
  record S() {}

  void test0(Object obj) {
    switch (obj) {
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>:
        System.out.println(i + 1);
      default:
    }
  }

  void test1(String s) {
    switch (s) {
      case "hello":
        System.out.println("hello");
      case "world":
        System.out.println("world");
      case <error descr="Illegal fall-through to a pattern">String str</error> when str.isEmpty():
        System.out.println("an empty string");
      case null:
        System.out.println("null");
    }
  }

  void test2(Object obj) {
    switch (obj) {
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
      case R():
      case S():
        System.out.println(42);
        break;
      default:
    }
  }

  void test3(Object obj) {
    switch (obj) {
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
      default:
        System.out.println(42);
    }
  }

  void test4(Object obj) {
    switch (obj) {
      case null:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>:
        System.out.println(s);
      default:
    }
  }

  void test5(String s) {
    switch (s) {
      case null:
      case "hello":
        System.out.println("hello");
        break;
      default:
    }
  }

  void test6(Object obj) {
    switch (obj) {
      case S():
      case null:
      case R():
        System.out.println("blah blah blah");
        break;
      default:
    }
  }

  void test7(Object obj) {
    switch (obj) {
      case String s:
        System.out.println(s);
      case R():
        System.out.println("It's either an R or a string");
        break;
      default:
    }
  }


  void test8(Object obj) {
    switch (obj) {
      case String s:
        System.out.println("String: " + s);
      case <error descr="Illegal fall-through to a pattern">Integer i</error>:
        System.out.println(i + 1);
      default:
    }
  }

  void test9(Object obj) {
    switch (obj) {
      case R():
      case S():
        System.out.println("Either R or an S");
        break;
      default:
    }
  }

  void test10(Object obj) {
    switch (obj) {
      case null:
      case R():
        System.out.println("Either null or an R");
        break;
      default:
    }
  }

  void test11(Integer integer) {
    switch (integer) {
      case 1, 2:
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, Integer i when i == 42:
        System.out.println("blah blah blah");
        break;
      default: System.out.println("null");
    }
  }

  void test12(Integer integer) {
    switch (integer) {
      case 1, 2, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a single case pattern">Integer i1</error> when i1 > 5:
      case null:
        System.out.println("blah blah blah");
        break;
      default:
    }
  }

  void test13(Object obj) {
    switch (obj) {
      case String s, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error> -> {}
      default -> {}
    }
  }

  void test14(Object obj) {
    switch (obj) {
      case null, String s when s.isEmpty()<error descr="':' or '->' expected"><error descr="Unexpected token">,</error></error> Integer i<error descr="';' expected"> </error><error descr="Cannot resolve symbol 'when'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">when</error> <error descr="Variable 'i' is already defined in the scope">i</error><error descr="';' expected"> </error><error descr="Unexpected token">==</error> <error descr="Not a statement">42</error> <error descr="Unexpected token">-></error> {}
      default -> {}
    }
  }

  void test15(Object obj) {
    switch (obj) {
      case String s when s.isEmpty()<error descr="':' or '->' expected"><error descr="Unexpected token">,</error></error> null, <error descr="Expression expected">Integer</error><error descr="';' expected"> </error><error descr="Not a statement">i -> {}</error>
      default -> {}
    }
  }

  void test16(Object obj) {
    switch (obj) {
      case String s, Integer i, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error> -> {}
      default -> {}
    }
  }

  void test17(String s) {
    switch (s) {
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, "hello", "world" -> {}
      default -> {}
    }
  }

  void test18(String s) {
    switch (s) {
      case "hello", "world", null, String str when <error descr="Cannot resolve symbol 'str'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">str</error>.isEmpty() -> {}
      default -> {}
    }
  }

  void test19(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, String str -> {}
    }
  }

  void test20(Object obj) {
    switch (obj) {
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, S(), R() -> {}
      default -> {}
    }
  }

  void test21(Object obj) {
    switch (obj) {
      case S(), <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, R() -> {}
      default -> {}
    }
  }

  void test22(Object obj) {
    switch (obj) {
      case String s when s.isEmpty()<error descr="':' or '->' expected"><error descr="Unexpected token">,</error></error> null, <error descr="Expression expected">Integer</error><error descr="';' expected"> </error><error descr="Not a statement">i -> {}</error>
      default -> {}
    }
  }

  void test23(Object obj) {
    switch (obj) {
      case String s, Integer i, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error> -> {}
      default -> {}
    }
  }

  void test24(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, String str -> {}
    }
  }

  void test25(String s) {
    switch (s) {
      case "hello", "world", String str, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error> -> {}
    }
  }

  void test26(Object obj) {
    switch (obj) {
      case String s:
      case null:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
      default:
        throw new IllegalStateException("Unexpected value: " + obj);
    }
  }

  void test27(Object obj) {
    switch (obj) {
      case String s:
        System.out.println(s);
      case null:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
      default:
        throw new IllegalStateException("Unexpected value: " + obj);
    }
  }

  void test28(Object obj) {
    switch (obj) {
      case Integer i, <error descr="Invalid case label combination: a case label must not consist of more than one case pattern">String str</error> -> {}
      default -> {}
    }
  }

  void test28(String s) {
    switch (s) {
      case String str, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a single case pattern">"hello"</error>, "world" -> {}
    }
  }

  void test29(Object obj) {
    switch (obj) {
      case null, default -> {}
    }
  }

  void test30(Object obj) {
    switch (obj) {
      case <error descr="Invalid case label order: 'null' must be first and 'default' must be second">default</error>, null -> {}
    }
  }

  void test31(Object obj) {
    switch (obj) {
      case String s, <error descr="Default label not allowed here: 'default' can only be used as a single case label or paired only with 'null'">default</error> -> {}
    }
  }

  void test32(String s) {
    switch (s) {
      case "hello", "world", <error descr="Default label not allowed here: 'default' can only be used as a single case label or paired only with 'null'">default</error> -> {}
    }
  }

  void test33(String s) {
    switch (s) {
      case <error descr="The label for the default case must only use the 'default' keyword, without 'case'">default</error> -> {}
    }
  }
}
