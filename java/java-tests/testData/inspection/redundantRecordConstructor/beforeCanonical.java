// "Remove constructor" "true" 
record Rec(int x, int y) {
  public R<caret>ec(int x, int y) {
    this.x = x; // 1
    /*2*/
    this.y =/*3*/ y; // 4
    /*5*/
    //6
  }
}