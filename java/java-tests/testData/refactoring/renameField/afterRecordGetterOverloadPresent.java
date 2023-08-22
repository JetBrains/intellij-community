class Main {
  private record Rec(int <caret>baz, int y) {
    public Rec(int baz, int y) {
      this.baz = baz;
      this.y = y;
    }

    void baz(int y) {}
  }
}