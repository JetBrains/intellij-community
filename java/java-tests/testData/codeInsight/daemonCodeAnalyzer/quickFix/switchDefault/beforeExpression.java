// "Unwrap 'switch'" "true-preview"
class X {
  String test(int i) {
    return switch<caret>(i) { default -> "foo"; };
  }
}