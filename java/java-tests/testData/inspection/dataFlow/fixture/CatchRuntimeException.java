import org.jetbrains.annotations.NotNull;

class BrokenAlignment {

  @NotNull
  Object test1() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (RuntimeException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  @NotNull
  Object test2() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (IllegalArgumentException | IllegalStateException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  @NotNull
  Object test3() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (AssertionError | IllegalStateException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  public void bar(@NotNull Object foo) {
    if (<warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>);
  }

  public void bar2(@NotNull Object foo) {
    assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>;
    try { }
    catch (java.lang.RuntimeException ex) { }
  }

}