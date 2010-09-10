import org.jetbrains.annotations.*;

class Test {
  String myFoo = "";

  String myFoo1 = null;

  String myFoo2 = foo2();
  @NotNull String foo2() { return "";}

  String myFoo3 = foo3();
  @Nullable String foo3() { return null;}

  String myFoo4;
  void setFoo4() {
    myFoo4 = "";
  }

  final String myFoo5;
  final String myFoo6;
  final String myFoo7;
  final String myFoo8;
  final String myFoo9;
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

  String foo10(boolean flag) {
    return flag ? foo2() : foo3();
  }
}