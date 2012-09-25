class A {
  void ba<caret>r(B b) {
    new Runnable() {
      @Override
      public void run() {
        System.out.println(b);
      }
    }.run();
  }
}

class B {

}