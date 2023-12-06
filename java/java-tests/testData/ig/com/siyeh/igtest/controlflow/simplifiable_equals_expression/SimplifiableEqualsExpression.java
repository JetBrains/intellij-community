package com.siyeh.igtest.controlflow.simplifiable_equals_expression;

import java.util.*;

public class SimplifiableEqualsExpression {

  void foo(String namespace) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">namespace != null</warning> && namespace.equals("")) {
      return;
    }
  }

  void bar(String namespace) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">namespace == null</warning> || !namespace.equals("")) {
      return;
    }
  }

  void baz(Integer i) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">i != null</warning> && i.equals(1)) {
      return;
    }
  }

  void boz(String namespace) {
    if (namespace == null || namespace.equals("")) { // don't warn here
      return;
    }
  }

  void bas(String s) {
    if (<warning descr="Unnecessary 'null' check before 'equalsIgnoreCase()' call">s != null</warning> && s.equalsIgnoreCase("yes")) {
      return;
    }
  }

  private Optional<Long> getOptional() {
    return Optional.of(1L);
  }

  // IDEA-177798 Simplifiable equals expression: support non-constant argument
  public boolean foo(Long previousGroupHead) {
    Long aLong = getOptional().get();

    return <warning descr="Unnecessary 'null' check before 'equals()' call">previousGroupHead != null</warning> && previousGroupHead.equals(aLong);
  }

  void trimTest(String s1, String s2) {
    if(<warning descr="Unnecessary 'null' check before 'equals()' call">s1 == null</warning> || !s1.equals(s2.trim())) {
      System.out.println("...");
    }
  }
  
  private static final int DEBUG_LEVEL = 1;
  private boolean xyz;

  {
    String s1 = DEBUG_LEVEL > 0 ? "null" : null;
    String s2 = xyz ? null : "null";
    String s = getString();
    
    if(<warning descr="Unnecessary 'null' check before 'equals()' call">s == null</warning> || !s.equals(s1)) {
      System.out.println("...");
    }

    if(<warning descr="Unnecessary 'null' check before 'equals()' call">s == null</warning> || !s.equals(s2)) {
      System.out.println("...");
    }
  }

  private String s = getString();
  private final String s1 = DEBUG_LEVEL > 0 ? "foo" : null;
  private boolean flag = <warning descr="Unnecessary 'null' check before 'equals()' call">s != null</warning> && s.equals(s1);

  void test(List<String> list) {
    String s = list.get(0);
    if(s != null && s.equals(list.get(1))) {
      System.out.println("???");
    }
  }

  static String getString() {
    return Math.random() > 0.5 ? "x" : null;
  }
}
