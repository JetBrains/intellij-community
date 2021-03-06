class Main {
  private record Rec(int x, int y) {
  }
  
  {
    int i = new Rec(1, 0).<caret>x;
  }
}