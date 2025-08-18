import org.jetbrains.annotations.*;

class Test {
  @NotNull String myFoo = "";

  @Nullable String myFoo1 = null;

  @NotNull String myFoo2 = foo2();
  @NotNull String foo2() { return "";}

  @Nullable String myFoo3 = foo3();
  @Nullable String foo3() { return null;}

  String myFoo4;
  void setFoo4() {
    myFoo4 = "";
  }

  final @NotNull String myFoo5;
  final @Nullable String myFoo6;
  final @NotNull String myFoo7;
  final @Nullable String myFoo8;
  final String myFoo9;
  final @Nullable String myFoo10;

  final String myFoo11 = "";
  final @NotNull String myFoo12;
  final @Nullable String myFoo13 = null;
  final Runnable myFoo14 = new Runnable() {
    {foo();}
    @Nullable Object foo() {
      return null;
    }
    public void run() {}
  };

  /**
   * {@link #myFoo6}
   */
  Test(@NotNull String param, @Nullable String paramNullable, String simpleParam) {
    myFoo5 = "";
    myFoo6 = null;
    myFoo7 = param;
    myFoo8 = paramNullable;
    myFoo9 = simpleParam;
    myFoo10 = foo10(false);
    myFoo12 = "";
  }

  @Nullable String foo10(boolean flag) {
    return flag ? foo2() : foo3();
  }
}