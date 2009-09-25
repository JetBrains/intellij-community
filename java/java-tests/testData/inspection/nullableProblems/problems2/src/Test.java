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
  @Nullable public C getC() {return C;}
  public C getC2() {return C;}

  public void f1(@NotNull C p) {}
  public void f2(@NotNull C p) {}
  public void f3(@Nullable C p) {}
  public void f4(@Nullable C p) {}

  @Nullable @NotNull String f() { return null;}
}
