package com.siyeh.igtest.classlayout.final_method_in_final_class;

public class X {
  public static <warning descr="'static' method declared 'final'">final</warning> void foo() {}

  public final void foo1() {}

  public static void foo2() {}

  // incomplete code: suppress

  <error descr="Illegal combination of modifiers 'private' and 'public'">private</error> final

  <error descr="Illegal combination of modifiers 'public' and 'private'">public</error> static void main(String[] args) {}
}