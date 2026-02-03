// "Convert canonical constructor to compact form" "true" 
record Rec(int x, int y) {
  /**
   * Documented
   * @param x x
   * @param y y
   */
  public Rec(int x<caret>, int y) {
    this.x = x;
    this.y = y;
  }
}