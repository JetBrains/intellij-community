class ThrowNull {
  public void x() throws IllegalArgumentException, NullPointerException {
    if (true) {
      throw new IllegalArgumentException();
    }
    throw null;
  }
}