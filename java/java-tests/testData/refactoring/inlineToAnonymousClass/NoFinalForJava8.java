class InlineClassFinal {
  void sdf(String vp) {
    Object s = new M<caret>y(vp);
  }
}

class My {
  private final String v;
  public My(String v) {
    this.v = v;
  }
}