import org.jetbrains.annotations.NotNull

@SuppressWarnings("unused")
class GroovyInnerClass {
  GroovyInnerClass() {
    new Inner(null, "")
  }

  static void fail() {
    new Inner(new GroovyInnerClass(), "", null)
  }

  private class Inner {
    Inner(String s1, @NotNull String s2) { }
  }
}