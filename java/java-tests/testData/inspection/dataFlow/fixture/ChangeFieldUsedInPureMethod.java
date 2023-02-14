// IDEA-292808
class Test {
  private class Inner {
    private boolean firstCheckOk;
    private boolean secondCheckOk;
    public void firstCheck(boolean ok) {
      boolean oldOk = isOk();
      this.firstCheckOk = ok;
      boolean newOk = isOk();
      if (oldOk != newOk)
        System.out.println(newOk);
    }
    public void secondCheck(boolean ok) {
      boolean oldOk = isOk();
      this.secondCheckOk = ok;
      boolean newOk = isOk();
      if (oldOk != newOk)
        System.out.println(newOk);
    }
    public boolean isOk() {
      return firstCheckOk || secondCheckOk;
    }
  }
}