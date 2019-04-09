class Test{
  public void foo(boolean cond1, boolean cond2, boolean cond3) {
    if (cond1){
        NewMethodResult x = newMethod(cond2);
        if (x.exitKey == 1) return;
    }
    else if (cond3){
    }
    x();
  }

    NewMethodResult newMethod(boolean cond2) {
        if (cond2) return new NewMethodResult((1 /* exit key */));
        return new NewMethodResult((-1 /* exit key */));
    }

    static class NewMethodResult {
        private int exitKey;

        public NewMethodResult(int exitKey) {
            this.exitKey = exitKey;
        }
    }

    void x() {}
}
