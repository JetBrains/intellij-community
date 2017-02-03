class P {
    void foo() {
        foo();
        bazz(0);
    }

    private void <caret>bazz(int k) {
        bazz(k);
        bazz(k);
    }
    
    void m(int k) {
        new Runnable() {
          public void run() {}
          void mm() {
              bazz(k);
          }
        };
    }
}