import org.jspecify.annotations.*;

@NullMarked
class A {
  @NullMarked
  @NullUnmarked
  static String test(@Nullable String s, String s1) {
    return "";
  }
}
