import org.jspecify.annotations.*;

<warning descr="Conflicting nullability annotations">@NullMarked</warning>
<warning descr="Conflicting nullability annotations">@NullUnmarked</warning>
class Test {
}

class TestMethod {
  <warning descr="Conflicting nullability annotations">@NullMarked</warning>
  <warning descr="Conflicting nullability annotations">@NullUnmarked</warning>
  void method() {}
}