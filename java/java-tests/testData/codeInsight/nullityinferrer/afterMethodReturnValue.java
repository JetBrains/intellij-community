import org.jetbrains.annotations.*;

class Test {
  @Nullable
  String foo1() {
    return null;
  }

  @NotNull
  String foo2() {
    return "";
  }

  String foo3(String s) {
    return s;
  }

  String foo4(@NotNull String s) {
    return s.substring(0);
  }

  @NotNull
  Integer foo5(Integer i) {
    return i++;
  }

  @NotNull
  Integer foo6(Integer i) {
    if (i == 0) return 1;
    return i * foo6(i--);
  }

  @Nullable
  Integer foo7(boolean flag) {
    return flag ? null : 1;
  }

  @Nullable
  Integer foo8(boolean flag) {
    if (flag) {
      return null;
    }
    else {
      return 1;
    }
  }

  @Nullable
  String bar9() {
    return foo3("");
  }

  @Nullable
  String foo9() {
    return bar9();
  }


  @Nullable
  String bar10() {
    return foo3("");
  }

  @NotNull
  String bar101() {
    return foo3("");
  }

  @Nullable
  String foo10(boolean flag) {
    return flag ? bar10() : bar101();
  }

  @NotNull
  String foo11() {
    class Foo{
      @Nullable
      String mess() {
        return null;
      }
    }
    return "";
  }
}