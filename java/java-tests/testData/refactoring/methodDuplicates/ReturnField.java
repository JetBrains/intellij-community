class Return {
  private Return myReturn;
  private int myInt;

  public Return <caret>method() {
    myReturn = new Return();
    myReturn.myInt++;
    return myReturn;
  }

  public void contextLValue() {
    myReturn = new Return();
    myReturn.myInt++;
    myReturn = null;
  }

  public void contextNoUsage() {
    myReturn = new Return();
    myReturn.myInt++;
  }

  public void contextRValue() {
    myReturn = new Return();
    myReturn.myInt++;
    Return r = myReturn;
  }

  public void contextRValueQualified() {
    myReturn = new Return();
    myReturn.myInt++;
    Return r = this.myReturn;
  }
}
