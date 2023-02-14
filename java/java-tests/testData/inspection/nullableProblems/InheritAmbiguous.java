import typeUse.*;

abstract class A {
  class B {}

  @NotNull abstract A.B get();
}
abstract class C extends A {
  abstract A.@NotNull B get();
}
