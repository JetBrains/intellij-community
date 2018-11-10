import org.jetbrains.annotations.Contract;

class C {
  @Contract(value = "!null, _ -> true; null, null -> true; null, !null -> false", pure = true)
  boolean fooInverted(String s, String s2) {
    return s != null || s2 == null;
  }

  void bar() {
    fooInverted();
  }
}
