class PrefixExpression {
  void m(int i) {
    int j = switch(<caret>i++) {
      default -> 7;
    };
  }
}