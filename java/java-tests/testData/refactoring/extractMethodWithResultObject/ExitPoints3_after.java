class Test{
  void foo(boolean cond1, boolean cond2, boolean cond3) {
    if (cond1){
        NewMethodResult x = newMethod(cond2);
        if (x.exitKey == 1) return;
    }
    else if (cond3){
    }
  }

    NewMethodResult newMethod(boolean cond2) {
        if (cond2) return new NewMethodResult((1 /* exit key */));
        x();
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
