// "Convert canonical constructor to compact form" "false" 
record Rec(int x, int y) {
  public Rec(int x<caret>, int y) {
    this.x = y;
    this.y = x;
  }
}