class A {

    void leaveIt() {
    new Runnable() {
      public void run() {
        A.this.toString();
      }
    }
  }
}

class B extends A {
    void foo() {
      new Runnable() {
        public void run() {
          B.this.toString();
        }
      }
    }
}
