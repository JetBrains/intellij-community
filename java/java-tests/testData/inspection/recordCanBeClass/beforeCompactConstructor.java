// "Convert record to class" "true-preview"
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