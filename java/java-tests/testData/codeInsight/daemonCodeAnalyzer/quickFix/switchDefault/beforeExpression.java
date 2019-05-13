// "Unwrap 'switch'" "true"
class X {
  String test(int i) {
    return switch<caret>(i) { default -> "foo"; };
  }
}