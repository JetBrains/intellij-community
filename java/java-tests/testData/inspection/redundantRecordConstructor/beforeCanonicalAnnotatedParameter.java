// "Convert canonical constructor to compact form" "false" 
record Rec(int x, int y) {
  public Rec(@Anno int x<caret>, int y) {
    if (x < 0) throw new IllegalArgumentException();
    this.x = x;
    this.y = y;
  }
}

@interface Anno {}