package com.siyeh.igtest.bugs.string_equality;

public class StringEquality {

  void foo(String s, String t) {
    final boolean a = s == null;
    final boolean b = t <warning descr="String values are compared using '==', not 'equals()'">==</warning> s;
    final boolean c = t ==<EOLError descr="Expression expected"></EOLError><EOLError descr="';' expected"></EOLError>
  }

  void notEquals(String s, String t) {
    boolean a = s <warning descr="String values are compared using '!=', not 'equals()'">!=</warning> t;
  }
}
