package com.siyeh.igtest.classlayout.final_private_method;

public class FinalPrivateMethod {

  private <warning descr="'private' method declared 'final'">final</warning> void foo() {};

  <error descr="@SafeVarargs is not allowed on methods with fixed arity">@java.lang.SafeVarargs</error>
  private final void foo(String s) {}

  @java.lang.SafeVarargs
  private final <T> void foo(T... i) {}

  <error descr="Illegal combination of modifiers 'public' and 'private'">public</error> final

  <error descr="Illegal combination of modifiers 'private' and 'public'">private</error> void bar() {}
}
