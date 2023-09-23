import typeUse.NotNull;

interface Foo {
  String <warning descr="Overriding methods are not annotated">@NotNull</warning> [] foo();
  void foo(String <warning descr="Overriding method parameters are not annotated">@NotNull</warning>[] arg);
}
class Bar implements Foo {
  public String[] <warning descr="Not annotated method overrides method annotated with @NotNull">foo</warning>() {
    return new String[0];
  }
  
  public void foo(String[] <warning descr="Not annotated parameter overrides @NotNull parameter">arg</warning>) {
    
  }
}