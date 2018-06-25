class S {
  void f(Boolean override) {

    if (override == null) {
      //doSomething();
    } else if (override) {    // always false?
      //doOverride();
    }

  }
  public void te0(boolean b){
    Boolean c = false;
    //        if (b) c = true;
    if (<warning descr="Condition 'c' is always 'false'">c</warning>) {
    }
  }
  public void te1(boolean b){
    Boolean c = true;
    //        if (b) c = true;
    if (<warning descr="Condition 'c' is always 'true'">c</warning>) {
    }
  }
  public void te2(boolean b){
    Boolean c = false;
    if (b) c = true;
    if (c) {
    }
  }

  public void te3(boolean b){
    Boolean c = Boolean.FALSE;
    boolean o = !c;
    if (<warning descr="Condition 'o' is always 'true'">o</warning>) {
    }
  }
  public void te4(boolean b){
    Boolean c = Boolean.FALSE;
    boolean o = c;
    if (<warning descr="Condition 'o' is always 'false'">o</warning>) {
    }
  }
  public void te5(boolean b){
    Boolean c = Boolean.TRUE;
    boolean o = <warning descr="Condition 'b||c' is always 'true'">b||<warning descr="Condition 'c' is always 'true' when reached">c</warning></warning>;
    if (<warning descr="Condition 'o' is always 'true'">o</warning>) {
    }
  }
  public void te6(boolean b){
    Boolean c = Boolean.TRUE;
    boolean o = !c;
    <warning descr="Condition 'o' at the left side of assignment expression is always 'false'. Can be simplified">o</warning> |= c&b;
    if (o) {
    }
  }

  public void flushOriginal(boolean b){
    boolean o;
    {
      Boolean c = Boolean.FALSE;
      o = !c;
    }
    if (<warning descr="Condition 'o' is always 'true'">o</warning>) {
    }
  }
}
