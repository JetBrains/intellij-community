public record Rec(int a) {
  public Rec {
    if (a < 0) throw new IllegalArgumentException();
  }
}
