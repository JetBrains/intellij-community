import org.jetbrains.annotations.Contract;

class C {
  @Contract(value = "!null, _ -> false; null, null -> false; null, !null -> true", pure = true)
  boolean f<caret>oo(String s, String s2) {
    return s == null && s2 != null;
  }

  void bar() {
    foo();
  }
}
