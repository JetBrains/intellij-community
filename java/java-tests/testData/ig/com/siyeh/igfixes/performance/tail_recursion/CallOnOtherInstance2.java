class CallOnOtherInstance2 {
  private boolean duplicate;
  private Something something;
  private CallOnOtherInstance2 original;

  public Something foo() {
    if (!duplicate) {
      return something;
    } else {
      return
        //comment
        getOriginal().<caret>foo();
    }
  }

  private CallOnOtherInstance2 getOriginal() {
    return original;
  }
  private boolean isDuplicate() {
    return duplicate;
  }

  public static class Something {}
}