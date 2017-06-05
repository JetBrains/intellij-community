// "Convert to ThreadLocal" "true"
class Main {
  private final boolean propert<caret>y;

  Main3(boolean property) {
    if (property) {
      property = false;
    }
    this.property = property;
  }
}