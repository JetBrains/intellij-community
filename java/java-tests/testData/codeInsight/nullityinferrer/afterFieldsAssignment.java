import org.jetbrains.annotations.*;

class Test {
  @NotNull
  String myFoo = "";

  @Nullable
  String myFoo1 = null;

  @NotNull
  String myFoo2 = foo2();
  @NotNull String foo2() { return "";}

  @Nullable
  String myFoo3 = foo3();
  @Nullable String foo3() { return null;}

  String myFoo4;
  void setFoo4() {
    myFoo4 = "";
  }

  @NotNull
  final String myFoo5;
  @Nullable
  final String myFoo6;
  @NotNull
  final String myFoo7;
  @Nullable
  final String myFoo8;
  final String myFoo9;
  @Nullable
  final String myFoo10;

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
  }

  @Nullable
  String foo10(boolean flag) {
    return flag ? foo2() : foo3();
  }
}