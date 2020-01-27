// "Remove constructor" "true" 
record Rec(int x, int y) {
  public R<caret>ec(int x, int y) {
    this.x = x;
    this.y = y;
  }
}