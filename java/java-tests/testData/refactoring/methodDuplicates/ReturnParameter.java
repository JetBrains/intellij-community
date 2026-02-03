class Return {
  private Return myReturn;
  private int myInt;

  public Return <caret>method(Return p) {
    p.myInt--;
    return p;
  }

  public void contextLValue() {
    myReturn.myInt--;
    myReturn = null;
  }

  public void contextNoUsage() {
    myReturn.myInt--;
  }

  public void contextRValue() {
    myReturn.myInt--;
    Return r = myReturn;
  }

  public void contextRValueQualified() {
    myReturn.myInt--;
    Return r = this.myReturn;
  }
}
