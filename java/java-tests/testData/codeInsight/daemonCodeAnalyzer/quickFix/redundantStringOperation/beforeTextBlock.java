// "Fix all 'Redundant 'String' operation' problems in file" "true"
class X {
  void x(String message) {
    boolean underline = message.<caret>substring(1, 2).equals("""
      _""");
  }
}