class CallOnOtherInstance1 {
  private boolean duplicate;
  private Something something;
  private CallOnOtherInstance1 original;

  public Something getSomething() {
    if (something == null) {
      if (isDuplicate()) {
        final CallOnOtherInstance1 recursion = getOriginal();
        return recursion.<caret>getSomething();
      } else {
        something = new Something();
      }
    }
    return something;
  }

  private CallOnOtherInstance1 getOriginal() {
    return original;
  }
  private boolean isDuplicate() {
    return duplicate;
  }

  public static class Something {}
}