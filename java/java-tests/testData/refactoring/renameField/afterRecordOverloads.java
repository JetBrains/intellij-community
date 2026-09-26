class Main {
  private record Rec(int baz, int y) {
    void <caret>baz(int value) {}
  }
  
  
  
  {
    int i = new Rec(1, 0).baz();
  }
}