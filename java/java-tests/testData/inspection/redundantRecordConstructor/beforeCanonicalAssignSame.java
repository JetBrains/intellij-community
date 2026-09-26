// "Convert canonical constructor to compact form" "false" 
record Rec(int x, int y) {
  public Rec(int x<caret>, int y) {
    this.x = y;
    // 1
    this.y /*2*/= y; //3
    //4
  }
}