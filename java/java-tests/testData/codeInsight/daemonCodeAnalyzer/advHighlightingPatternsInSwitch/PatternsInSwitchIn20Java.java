class X {
  void testDominance1(Object obj) {
    switch (obj) {
      default -> System.out.println("default");
      case <error descr="Label is dominated by a preceding case label 'default'">Integer i</error> -> System.out.println("Integer");
      case <error descr="Label is dominated by a preceding case label 'default'">String s when s.isEmpty()</error> -> System.out.println("empty String");
      case <error descr="Label is dominated by a preceding case label 'default'">null</error> -> System.out.println("null");
    }
  }

  void testDominance2(Object obj) {
    switch (obj) {
      case null, default -> System.out.println("null or default");
      case <error descr="Label is dominated by a preceding case label 'default'">Integer i</error> -> System.out.println("Integer");
      case <error descr="Label is dominated by a preceding case label 'default'">String s when s.isEmpty()</error> -> System.out.println("empty String");
    }
  }

  void testDominance3(String s) {
    switch (s) {
      default -> System.out.println("default");
      case "blah blah blah" -> System.out.println("blah blah blah");
      case <error descr="Label is dominated by a preceding case label 'default'">null</error> -> System.out.println("null");
    }
  }

  void testDominance4(String s) {
    switch (s) {
      case null, default -> System.out.println("null, default");
      case <error descr="Label is dominated by a preceding case label 'default'">"blah blah blah"</error> -> System.out.println("blah blah blah");
    }
  }

  void test(String s) {
    switch (s) {
      case null, <error descr="'switch' has both a total pattern and a default label">default</error> -> System.out.println("null, default");
      case <error descr="'switch' has both a total pattern and a default label">String str</error> -> System.out.println("String");
    }
  }
}
