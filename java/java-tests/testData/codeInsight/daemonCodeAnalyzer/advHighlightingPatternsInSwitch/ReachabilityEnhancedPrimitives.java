class AAA{
  void testBoolean(boolean a) {
    switch (a) {
      case true:
        throw new IllegalArgumentException();
      case false:
        throw new IllegalArgumentException();
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void testBoolean2(Boolean a) {
    switch (a) {
      case true:
        throw new IllegalArgumentException();
      case null:
        throw new IllegalArgumentException();
      default:
        throw new IllegalStateException("Unexpected value: " + a);
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  void testFloat(float a) {
    switch (a) {
      case 1f:
        throw new IllegalArgumentException();
      case Number v:
        throw new IllegalStateException("Unexpected value: " + a);
    }
    <error descr="Unreachable statement">System.out.println();</error>
  }

  static void main() {
  }
}