// "Convert canonical constructor to compact form" "false" 
record Rec(int x, int y) {
  public Rec(@Anno int x<caret>, int y) {
    this.x = y;
    this.y = y;
  }
}

@interface Anno {}