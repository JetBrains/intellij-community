class Test {
  void bar() {
    foo(new Runnable() {
        public void run() {
            System.out.println(1);
            System.out.println(1);
        }
    });
  }
  
  void foo(Runnable anObject) {

      anObject.run();

  }
}