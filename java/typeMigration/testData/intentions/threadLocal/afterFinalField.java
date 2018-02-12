// "Convert to ThreadLocal" "true"
class Foo {
    private final ThreadLocal<Boolean> property = ThreadLocal.withInitial(() -> false);

  Foo(boolean property) {
    this.property.set(property);
  }
}