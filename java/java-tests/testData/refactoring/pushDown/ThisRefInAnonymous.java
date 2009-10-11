class A {
  void <caret>foo() {
    new Runnable() {
      public void run() {
        A.this.toString();
      }
    }
  }

  void leaveIt() {
    new Runnable() {
      public void run() {
        A.this.toString();
      }
    }
  }
}

class B extends A {
}
