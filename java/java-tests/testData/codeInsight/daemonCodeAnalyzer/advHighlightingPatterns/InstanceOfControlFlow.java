class X {

  void simpleIf(Object obj) {
    if (!(obj instanceof String s)) return;
    System.out.println(s.trim());
  }

  void ifParenthesizedPattern(Object obj) {
    if (!(obj instanceof ((String s)))) return;
    System.out.println(s.trim());
  }

  void ifElse(Object obj) {
    if (!(obj instanceof String s)) return;
    else {
      System.out.println(s.trim());
    }
    System.out.println(s.trim());
  }

  static boolean FLAG2 = true;
  static final boolean FLAG = true;

  void ifElseInfiniteLoop(Object obj) {
    if (obj instanceof String s) {}
    else {
      while (FLAG) {
        System.out.println("oops");
      }
    }
    System.out.println(s);
  }

  void ifElseInfiniteLoopLocal(Object obj) {
    final boolean FLAG = true;
    if (obj instanceof String s) {}
    else {
      while (FLAG) {
        System.out.println("oops");
      }
    }
    System.out.println(s);
  }

  void ifElseFiniteLoop(Object obj) {
    if (obj instanceof String s) {}
    else {
      while (FLAG2) {
        System.out.println("oops");
      }
    }
    System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
  }

  void ifElseInfiniteSelfRef(Object obj) {
    if (obj instanceof Boolean b) {
      while(b) {}
    }
    else {
      while(<error descr="Cannot resolve symbol 'b'">b</error>) {}
    }
    while(<error descr="Cannot resolve symbol 'b'">b</error>) {}
  }
  
  class Shadow {
    String b;

    void ifElseInfiniteSelfRef(Object obj) {
      if (obj instanceof Boolean b) {
        while(b) {}
      }
      else {
        while(b == "") {}
      }
      while(b == "") {}
    }
    
    void inverted(Object obj) {
      if (!(obj instanceof Integer b)) {}
      System.out.println(b.trim());
    }
  }
  
  native Object getNextObj();
  
  void testWhile(Object obj) {
    while (!(obj instanceof Integer x)) {
      obj = getNextObj();
    }
    System.out.println(x.intValue());
  }
  
  void testWhileWithBreak(Object obj) {
    while (!(obj instanceof Integer x)) {
      obj = getNextObj();
      if (obj instanceof String) break;
    }
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>.intValue());
  }
  
  void testDoWhile(Object obj) {
    do {
      obj = getNextObj();
      if (obj instanceof String) break;
    }
    while (!(obj instanceof Integer x));
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>.intValue());
  }
  
  void testDoWhileWithBreak(Object obj) {
    do {
      obj = getNextObj();
      if (obj instanceof String) break;
    }
    while (!(obj instanceof Integer x));
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>.intValue());
  }

  void testFor() {
    for (Object obj = getNextObj(); !(obj instanceof String s); obj = getNextObj()) {
      System.out.println("going further");
    }
    System.out.println("Found: "+s.trim());
  }
  
  void testLabel() {
    Object o = "hello";
    L0: if(!(o instanceof String s)) {
      return;
    }
    System.out.println(s.length());
  }
}