public class Main {
  record R() {}
  record S() {}

  void test0(Object obj) {
    switch (obj) {
      case <error descr="Illegal fall-through from a pattern">String s</error>:
      case <error descr="Illegal fall-through to a pattern">Integer i</error>:
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
      case <error descr="Illegal fall-through to a pattern">String str when str.isEmpty()</error>:
        System.out.println("an empty string");
      case null:
        System.out.println("null");
    }
  }

  void test2(Object obj) {
    switch (obj) {
      case <error descr="Illegal fall-through from a pattern">String s</error>:
      case R():
      case S():
        System.out.println(42);
        break;
      default:
    }
  }

  void test3(Object obj) {
    switch (obj) {
      case <error descr="Illegal fall-through from a pattern">String s</error>:
      default:
        System.out.println(42);
    }
  }

  void test4(Object obj) {
    switch (obj) {
      case null:
      case <error descr="Illegal fall-through to a pattern">String s</error>:
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
      case null, <error descr="Invalid case label combination">Integer i when i == 42</error>:
        System.out.println("blah blah blah");
        break;
      default: System.out.println("null");
    }
  }

  void test12(Integer integer) {
    switch (integer) {
      case 1, 2, <error descr="Invalid case label combination">Integer i1 when i1 > 5</error>:
      case null:
        System.out.println("blah blah blah");
        break;
      default:
    }
  }

  void test13(Object obj) {
    switch (obj) {
      case String s, <error descr="Illegal fall-through from a pattern">null</error> -> {}
      default -> {}
    }
  }

  void test14(Object obj) {
    switch (obj) {
      case null, <error descr="Invalid case label combination">String s when s.isEmpty()</error>, Integer i when i == 42 -> {}
      default -> {}
    }
  }

  void test15(Object obj) {
    switch (obj) {
      case String s when s.isEmpty(), <error descr="Illegal fall-through from a pattern">null</error>, Integer i -> {}
      default -> {}
    }
  }

  void test16(Object obj) {
    switch (obj) {
      case String s, <error descr="Illegal fall-through from a pattern">Integer i</error>, null -> {}
      default -> {}
    }
  }

  void test17(String s) {
    switch (s) {
      case null, <error descr="Invalid case label combination">"hello"</error>, "world" -> {}
      default -> {}
    }
  }

  void test18(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination">null</error>, String str when str.isEmpty() -> {}
      default -> {}
    }
  }

  void test19(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination">null</error>, String str -> {}
    }
  }

  void test20(Object obj) {
    switch (obj) {
      case null, <error descr="Invalid case label combination">S()</error>, R() -> {}
      default -> {}
    }
  }

  void test21(Object obj) {
    switch (obj) {
      case S(), <error descr="Illegal fall-through from a pattern">null</error>, R() -> {}
      default -> {}
    }
  }

  void test22(Object obj) {
    switch (obj) {
      case String s when s.isEmpty(), <error descr="Illegal fall-through from a pattern">null</error>, Integer i -> {}
      default -> {}
    }
  }

  void test23(Object obj) {
    switch (obj) {
      case String s, <error descr="Illegal fall-through from a pattern">Integer i</error>, null -> {}
      default -> {}
    }
  }

  void test24(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination">null</error>, String str -> {}
    }
  }

  void test25(String s) {
    switch (s) {
      case "hello", "world", <error descr="Invalid case label combination">String str</error>, null -> {}
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
}
