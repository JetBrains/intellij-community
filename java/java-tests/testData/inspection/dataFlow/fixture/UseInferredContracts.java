import org.jetbrains.annotations.Nullable;

class Doo {

  boolean isNotNull(@Nullable Object o) {
    return o != null;
  }

  void foo(@Nullable String s) {
    if (isNotNull(s)) {
      System.out.println(s.length());
    }
  }

}
