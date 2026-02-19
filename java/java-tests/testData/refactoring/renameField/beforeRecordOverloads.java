class Main {
  private record Rec(int x, int y) {
    void <caret>x(int value) {}
  }
  
  
  
  {
    int i = new Rec(1, 0).x();
  }
}