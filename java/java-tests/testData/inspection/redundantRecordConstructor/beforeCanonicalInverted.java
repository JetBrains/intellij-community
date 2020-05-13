// "Remove constructor" "true" 
record Rec(int x, int y) {
  public R<caret>ec(int x, int y) {
    this.y = y;
    this.x = x;
  }
}