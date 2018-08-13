class StringEquality {
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
      <warning descr="Switch label 'case \"foo\":' is unreachable">case "foo":</warning>
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
    if(a == b) return true;
    if(a instanceof String && b instanceof String) {
      // false-positive
      return <warning descr="Result of '((String)a).equals((String)b)' is always 'false'">((String)a).equals((String)b)</warning>;
    }
    return false;
  }
}