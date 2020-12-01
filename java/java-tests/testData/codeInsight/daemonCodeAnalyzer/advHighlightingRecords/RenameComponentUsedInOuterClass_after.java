class Main {
  private record Rec(int baz, int y) {
  }
  
  {
    int i = new Rec(1, 0).baz;
  }
}