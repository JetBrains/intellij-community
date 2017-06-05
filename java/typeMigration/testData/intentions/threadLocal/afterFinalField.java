// "Convert to ThreadLocal" "true"
class Foo {
  private final ThreadLocal<Boolean> property;

  Foo(boolean property) {
    this.property = ThreadLocal.withInitial(() -> property);
  }
}