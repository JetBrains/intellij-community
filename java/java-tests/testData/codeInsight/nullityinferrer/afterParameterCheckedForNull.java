import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  void bar(@Nullable String str) {
    if (str == null) {
      foo(str);
    }
  }

  String foo(String str) {
    return str;
  }

  @NotNull String foo1(@Nullable String str) {
    if (str == null) return "null";
    return (str);
  }

  @NotNull String foo2(@Nullable String str) {
    if (str == null) return "null";
    return ((String)str);
  }

  @NotNull String fram(@Nullable String str, boolean b) {
    if (str != null) {
      return b ? str : "not null strimg";
    }
    return "str was null";
  }




}