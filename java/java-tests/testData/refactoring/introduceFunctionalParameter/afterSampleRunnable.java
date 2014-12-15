class Test {
  void bar() {
    foo(new Runnable() {
        public void run() {
            System.out.println("");
            System.out.println("");
        }
    });
  }
  
  static void foo(Runnable anObject) {

      anObject.run();

  }
}