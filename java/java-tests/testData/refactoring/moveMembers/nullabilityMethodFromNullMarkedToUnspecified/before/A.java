import org.jspecify.annotations.*;

@NullMarked
class A {
  static String test(String s, @Nullable String s1) {
    return s1 == null ? s : s1;
  }
}
