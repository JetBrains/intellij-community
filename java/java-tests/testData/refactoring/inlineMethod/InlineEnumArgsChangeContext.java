enum G {
  VALUE(100),
  REF(VALUE.getG<caret>etGet());

  private final int myGetGet;

  G(final int groupNumber) {
    myGetGet = groupNumber;
  }

  public int getGetGet() {
    return myGetGet;
  }
}