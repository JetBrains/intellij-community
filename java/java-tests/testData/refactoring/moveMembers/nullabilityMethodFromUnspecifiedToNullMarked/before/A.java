import org.jspecify.annotations.*;

class A {
  static @NonNull String test(@NonNull String s, String s1) {
    return s1 == null ? s : s1;
  }
}
