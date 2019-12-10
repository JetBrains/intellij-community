import typeUse.*;

class App {
  void foo1(@NotNull String @NotNull ... args) {}
  void foo2(String @NotNull ... args) {}
  void foo3(@NotNull String ... args) {}

  void use() {
    String[] nullArr = null;
    foo1(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">nullArr</warning>);
    nullArr = null;
    foo2(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">nullArr</warning>);
    nullArr = null;
    foo3(<warning descr="Passing 'null' argument to non-annotated parameter">nullArr</warning>);
    foo1(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, "");
    foo2(null, "");
    foo3(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, "");
  }
}