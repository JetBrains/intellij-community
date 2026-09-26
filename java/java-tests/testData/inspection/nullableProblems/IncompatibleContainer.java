import org.jspecify.annotations.*;

<warning descr="Conflicting nullability annotations">@NullMarked</warning>
<warning descr="Conflicting nullability annotations">@NullUnmarked</warning>
class Test {
}

class TestMethod {
  <warning descr="Conflicting nullability annotations">@NullMarked</warning>
  <warning descr="Conflicting nullability annotations">@NullUnmarked</warning>
  // @NullMarked and @NullUnmarked cancel each other out
  // so @NonNull should not be highlighted as redundant.
  void method(@NonNull String s) {}
}