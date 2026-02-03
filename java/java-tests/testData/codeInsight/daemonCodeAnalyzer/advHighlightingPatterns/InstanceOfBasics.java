class X {
  void expressions(Object obj) {
    boolean b1 = obj instanceof String s && s.isEmpty();
    boolean b2 = !(obj instanceof String s) && <error descr="Cannot resolve symbol 's'">s</error>.isEmpty();
    boolean b3 = obj instanceof String s || <error descr="Cannot resolve symbol 's'">s</error>.isEmpty();
    boolean b4 = !(obj instanceof String s) || s.isEmpty();
    boolean b5 = obj instanceof String s ? s.isEmpty() : obj == null;
    boolean b6 = !(obj instanceof String s) ? obj == null : s.isEmpty();
    boolean b7 = obj instanceof String s ? s.isEmpty() : <error descr="Cannot resolve symbol 's'">s</error>.isEmpty();
    boolean b8 = !(obj instanceof String s) ? <error descr="Cannot resolve symbol 's'">s</error>.isEmpty() : s.isEmpty();
  }
  
  void twoPatterns(Object o1, Object o2) {
    if (o1 instanceof String s1 && o2 instanceof String s2 && s1.startsWith(s2)) {}
    if ((o1 instanceof String s1 && o2 instanceof String s2) && s1.startsWith(s2)) {}
    if (o1 instanceof String s1 && (o2 instanceof String s2 && s1.startsWith(s2))) {}
    if (o1 instanceof String s1 && !(o2 instanceof String s2) && s1.startsWith(<error descr="Cannot resolve symbol 's2'">s2</error>)) {}
  }
  
  void polyadicInCondition(Object o1, Object o2) {
    boolean b1 = o1 instanceof String s1 && o2 instanceof String s2 ? s1.isEmpty() && s2.isEmpty() : false;
    boolean b2 = o1 instanceof String s1 && !(o2 instanceof String s2) ? s1.isEmpty() : <error descr="Cannot resolve symbol 's2'">s2</error>.isEmpty();
  }
  
  void ifThenSimple(Object o) {
    if (o instanceof String s) {
      System.out.println(s.trim());
    } else {
      System.out.println(<error descr="Cannot resolve symbol 's'">s</error>.trim());
    }
    if (!(o instanceof String s)) {
      System.out.println(<error descr="Cannot resolve symbol 's'">s</error>.trim());
    } else {
      System.out.println(s.trim());
    }
  }
  
  interface Node {
    Object next();
    String name();
  }
  
  void whileSimple(Object o) {
    while (o instanceof Node n) {
      o = n.next();
    }
  }
  
  void whileNot(Object o) {
    while (!(o instanceof Node n)) {
      o = <error descr="Cannot resolve symbol 'n'">n</error>.next();
    }
  }

  void forSimple(Object o) {
    for (; o instanceof Node n; o = n.next()) {
      System.out.println(n.name());
    }
  }

}