class Return {
  private Return myReturn;
  private int myInt;

  public Return <caret>method() {
    Return r = new Return();
    r.myInt >>= 1;
    return r;
  }

  public void contextLValue() {
    Return r = new Return();
    r.myInt >>= 1;
    r = null;
  }

  public void contextNoUsage() {
    Return r = new Return();
    r.myInt >>= 1;
  }

  public void contextRValue() {
    Return r = new Return();
    r.myInt >>= 1;
    Return r2 = r;
  }
}
