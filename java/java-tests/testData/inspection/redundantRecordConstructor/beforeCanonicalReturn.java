// "Remove constructor" "false" 
record Rec(int x, int y) {
  public R<caret>ec(int x, int y) {
    if (x < 0) {
      this.x = 0;
      this.y = 0;
      return;
    }
    this.x = x;
    this.y = y;
  }
}