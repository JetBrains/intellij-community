class StringEquality {
  void testNewString(char[] c) {
    if (new String(c).equals("FOO")) {}
  }

  void ifChain(String s) {
    if (s.equals("foo")) {

    }
    else if (s.equals("bar")) {

    }else if(<warning descr="Condition '\"foo\".equals(s)' is always 'false'">"foo".equals(s)</warning>) {

    }
  }

  void switchAfterIf(String s) {
    if (s.equals("foo")) {
      return;
    }
    switch(s) {
      case "bar":
      case "baz":
      case <warning descr="Switch label '\"foo\"' is unreachable">"foo"</warning>:
    }
  }

  void lengths(String s, String s1) {
    if(<warning descr="Condition 's.equals(s1) && s.length() != s1.length()' is always 'false'">s.equals(s1) && <warning descr="Condition 's.length() != s1.length()' is always 'false' when reached">s.length() != s1.length()</warning></warning>) {}
    if(<warning descr="Condition 's.length() != s1.length() && s.equals(s1)' is always 'false'">s.length() != s1.length() && <warning descr="Condition 's.equals(s1)' is always 'false' when reached">s.equals(s1)</warning></warning>) {}
  }

  // IDEA-197195
  void foo() {
    String v = "Foo";
    String vv = "FooFoo";
    String vvv = vv.substring(3);
    System.out.println(<warning descr="Result of 'v.equals(vv)' is always 'false'">v.equals(vv)</warning>);
    System.out.println(<warning descr="Condition 'v == vv' is always 'false'">v == vv</warning>);

    System.out.println(<warning descr="Result of 'v.equals(vvv)' is always 'true'">v.equals(vvv)</warning>);
    System.out.println(v == vvv); // Unsure: strings are equal by content, but DFA does not know whether they are equal by reference

    System.out.println(<warning descr="Result of 'vv.equals(vvv)' is always 'false'">vv.equals(vvv)</warning>);
    System.out.println(<warning descr="Condition 'vv == vvv' is always 'false'">vv == vvv</warning>);
  }

  boolean compare(Object a, Object b) {
    if(a == b) {
      if(a instanceof String) {
        return <warning descr="Result of '((String)a).equals(b)' is always 'true'">((String)a).equals(b)</warning>;
      }
      return true;
    }
    if(a instanceof String && b instanceof String) {
      return ((String)a).equals(b);
    }
    return false;
  }

  static final String SENTINEL = "foo";

  void test(Object o) {
    if(o == SENTINEL) {
      System.out.println("oops");
    } else {
      System.out.println(((Number)o).longValue());
    }
  }

  String internFoo(String s) {
    if (s.equals("foo")) {
      // "foo" is often used, intern it
      s = "foo";
    }
    return s;
  }

  void length(String s) {
    if(!s.startsWith("--") || <warning descr="Condition 's.equals(\".\")' is always 'false' when reached">s.equals(".")</warning>) {
      System.out.println("invalid parameter");
    }
  }

  interface X {
    Object getY();
  }

  void testWithCanonicalization(Object obj) {
    if(obj instanceof X) {
      X x = (X)obj;
      if (x.getY() instanceof String) {
        System.out.println("oops");
      }
    }
  }

  void testObject() {
    Object x = " foo ".trim();
    Object y = " foo ".trim();
    if (x == y) {}
  }

  void testIncorrect(String s) {
    if(<error descr="Operator '==' cannot be applied to 'java.lang.String', 'int'">s == s.length()</error>) {}
  }

  void testTrim() {
    System.out.println(" EQ ".trim() == "EQ");
  }
}