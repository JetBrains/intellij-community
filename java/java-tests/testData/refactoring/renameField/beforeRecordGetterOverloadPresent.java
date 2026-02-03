class Main {
  private record Rec(int <caret>x, int y) {
    public Rec(int x, int y) {
      this.x = x;
      this.y = y;
    }

    void x(int y) {}
  }
}