public class S {
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
            if (c) {
            }
        }
        public void te1(boolean b){
            Boolean c = true;
    //        if (b) c = true;
            if (c) {
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
        if (o) {
        }
    }
    public void te4(boolean b){
        Boolean c = Boolean.FALSE;
        boolean o = c;
        if (o) {
        }
    }
    public void te5(boolean b){
        Boolean c = Boolean.TRUE;
        boolean o = b||c;
        if (o) {
        }
    }
    public void te6(boolean b){
        Boolean c = Boolean.TRUE;
        boolean o = !c;
        o |= c&b;
        if (o) {
        }
    }

  public void flushOriginal(boolean b){
    boolean o;
    {
      Boolean c = Boolean.FALSE;
      o = !c;
    }
    if (o) {
    }
  }
}
