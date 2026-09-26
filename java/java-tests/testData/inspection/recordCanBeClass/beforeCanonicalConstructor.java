// "Convert record to class" "true-preview"
<caret>record Range(int x, int y) implements Cloneable {
  Range(int x, int y) {
    if (x > y) {
      throw new IllegalArgumentException();
    }
    this.x = x;
    this.y = y;
  }
}