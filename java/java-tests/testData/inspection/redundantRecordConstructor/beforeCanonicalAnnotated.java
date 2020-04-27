// "Convert canonical constructor to compact form" "true" 
record Rec(int x, int y) {
  @Deprecated
  public Rec(int x<caret>, int y) {
    this.x = x;
    this.y = y;
  }
}