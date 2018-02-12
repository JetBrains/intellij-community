// "Convert to ThreadLocal" "true"
class Main {
    private final ThreadLocal<Boolean> property = ThreadLocal.withInitial(() -> false);

  Main3(boolean property) {
    if (property) {
      property = false;
    }
    this.property.set(property);
  }
}