class Test {
  public boolean isConsideredFinal(int cl) {
    return haveSeenAllExp<caret>ectedAtCl(cl);
  }

  private boolean haveSeenAllExpectedAtCl(int cl) {
    return cl == cl();
  }

  int cl() {
    return 0;
  }
}