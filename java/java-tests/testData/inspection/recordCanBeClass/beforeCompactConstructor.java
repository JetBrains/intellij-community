// "Convert record to class" "true"
record <caret>Range(int x, int y) {
  /**
   * Checks invariant
   */
  Range {
    if (x > y) {
      throw new IllegalArgumentException();
    }
  }
}