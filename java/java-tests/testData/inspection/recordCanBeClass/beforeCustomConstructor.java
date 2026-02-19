// "Convert record to class" "true-preview"
record <caret>Range(int x, int y) {
  static final Range ZERO = new Range(0);
  
  Range(int x) {
    this(x, x);
  }
}