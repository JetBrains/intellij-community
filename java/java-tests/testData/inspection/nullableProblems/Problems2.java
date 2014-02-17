import org.jetbrains.annotations.*;

class C {
  public static C C = null;
  @NotNull public C getC() {return C;}
  @NotNull public C getC2() {return C;}

  public void f1(@Nullable C p) {}
  public void f2(@NotNull C p) {}
  public void f3(@Nullable C p) {}
  public void f4(@NotNull C p) {}
}

class CC extends C {
  <warning descr="Method annotated with @Nullable must not override @NotNull method">@Nullable</warning> public C getC() {return C;}
  public C <warning descr="Not annotated method overrides method annotated with @NotNull">getC2</warning>() {return C;}

  public void f1(<warning descr="Parameter annotated @NotNull must not override @Nullable parameter">@NotNull</warning> C p) {}
  public void f2(@NotNull C p) {}
  public void f3(@Nullable C p) {}
  public void f4(@Nullable C p) {}

  <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@NotNull</warning> String f() { return null;}
}
