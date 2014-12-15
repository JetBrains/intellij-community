class Test {
  void bar() {
    foo(1, new Runnable() {
        public void run() {
            System.out.println(1);
            System.out.println(1);
        }
    });
  }
  
  void foo(int i, Runnable anObject) {
    if (i > 0) {

        anObject.run();

    }
  }
}