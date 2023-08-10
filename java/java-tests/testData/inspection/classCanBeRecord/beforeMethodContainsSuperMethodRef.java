// "Convert to a record" "false"
class <caret>R {
  final int first;

  R(int first) {
    this.first = first;
  }

  @Override
  public String toString() {
    Supplier<String> toString = super::toString;
    return toString.get() + " " + first;
  }
}