package com.siyeh.igtest.bugs.string_concatenation_missing_whitespace;

class Concatenations {

  void foo(int i) {
    System.out.println("SELECT column" <warning descr="Whitespace may be missing in string concatenation">+</warning>
                       "FROM table");
    System.out.println("no:" + i);
    System.out.println("i" <warning descr="Whitespace may be missing in string concatenation">+</warning> i);
    System.out.println("i" <warning descr="Whitespace may be missing in string concatenation">+</warning> ((String)"j"));
    System.out.println('{' + "a" + '\'');
    String.format("aaaa%n" + "bbbb");
  }

  String get(String s) {
    return s + "serendipity";
  }

  private int outgoing = 0;

  void addOutgoing(int howMany) {
    assert outgoing + howMany >= 0 : outgoing + howMany + " must be >= 0";
  }
}
class testclass {
  private static final String WHITESPACE = " TEST ";
  private String s = "";

  public testclass() {
    s = "";
    s += WHITESPACE + "string";

    s = "";
    s += " TEST " + "string";
  }
}