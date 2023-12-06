class ExplititTypeArguments2 {
  void x(boolean x) {
    if (x <caret>? a() : (x ? (Boolean)a() : a())) ;
  }

  private static <T> T a() {
    return null;
  }
}