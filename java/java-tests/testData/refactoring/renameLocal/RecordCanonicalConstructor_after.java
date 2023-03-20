public record Bar(int i) {
  public Bar(String s) {
    this(s.length());
  }
}