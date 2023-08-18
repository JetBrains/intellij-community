class X {
  void testDominance1(Object obj) {
    switch (obj) {
      default -> System.out.println("default");
      case <error descr="Label is dominated by a preceding case label 'default'">Integer i</error> -> System.out.println("Integer");
      case <error descr="Label is dominated by a preceding case label 'default'">String s</error> when s.isEmpty() -> System.out.println("empty String");
      case <error descr="Label is dominated by a preceding case label 'default'">null</error> -> System.out.println("null");
    }
  }

  void testDominance2(Object obj) {
    switch (obj) {
      case null, default -> System.out.println("null or default");
      case <error descr="Label is dominated by a preceding case label 'default'">Integer i</error> -> System.out.println("Integer");
      case <error descr="Label is dominated by a preceding case label 'default'">String s</error> when s.isEmpty() -> System.out.println("empty String");
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

  void testUnconditionalPatternAndDefault1(String s) {
    switch (s) {
      case null, <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("null, default");
      case <error descr="'switch' has both an unconditional pattern and a default label">String str</error> -> System.out.println("String");
    }
  }

  void testUnconditionalPatternAndDefault2(Integer j) {
    switch (j) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Integer i</error> when true  -> System.out.println("An integer");
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("default");
    }
  }

  void testDuplicateUnconditionalPattern1(Integer j) {
    switch (j) {
      case <error descr="Duplicate unconditional pattern">Integer i</error> when true -> System.out.println("An integer");
      case <error descr="Duplicate unconditional pattern">Number number</error> -> System.out.println("An integer");
    }
  }

  void testDuplicateUnconditionalPattern2(Integer j) {
    switch (j) {
      case <error descr="Duplicate unconditional pattern">Integer i</error> when true -> System.out.println("An integer");
      case <error descr="Duplicate unconditional pattern">Integer i</error> -> System.out.println("An integer");
    }
  }
  
  record R1() {}
  record R2() {}
  
  void testNoVars(Object obj) {
    switch(obj) {
      case R1(), <error descr="Invalid case label combination: a case label must not consist of more than one case pattern">R2()</error> -> {}
      default -> {}
    }
  }

  void testCombination(Integer i) {
    switch (i) {
      case Integer a, <error descr="Invalid case label combination: a case label must not consist of more than one case pattern">Integer b</error> when i > 0 -> System.out.println(1);
      case 1, 2, 3, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a single case pattern">Integer c</error> when i > 0 -> System.out.println(1);
      case Integer d, <error descr="Invalid case label combination: a case label must consist of either a list of case constants or a single case pattern">4</error> when i > 0 -> System.out.println(1);
    }
  }
}
