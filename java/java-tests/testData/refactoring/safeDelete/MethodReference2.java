class Safe {
  public String x(int <caret>i) {
    return "yes";
  }

  static void main() {
    final Safe safe = new Safe();
    IntFunction<String> f = safe::x;
  }
}