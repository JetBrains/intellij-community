class Main {

  private int sideEffectCounter;


  public Main() {

    this.sideEffectCounter = 1;
  }

  private Object checkNullnessAndGet(Object obj) {
    return obj == null ? fooBar() : null;
  }

  private Object fooBar() {
    sideEffectCounter++;
    return null;
  }

  public final void doSomething(Object obj) {
    try {
      check<caret>NullnessAndGet(null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}