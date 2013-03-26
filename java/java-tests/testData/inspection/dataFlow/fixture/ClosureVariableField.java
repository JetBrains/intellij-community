class X {

  void foo() {

    final MyReference isAlive = new MyReference();
    if (isAlive.getValue()) {
      new Runnable() {
        @Override
        public void run() {
          if (isAlive.getValue()) {
            System.out.println();
          }
        }
      };
    }

  }
  void foo2() {

    final MyReference isAlive = new MyReference();
    if (isAlive.value) {
      new Runnable() {
        @Override
        public void run() {
          if (isAlive.value) {
            System.out.println();
          }
        }
      };
    }

  }


}

class MyReference {
  boolean value;

  boolean getValue() {
    return value;
  }
}