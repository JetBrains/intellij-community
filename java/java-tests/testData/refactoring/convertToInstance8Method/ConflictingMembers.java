class Test {
  int i;
  void run() {}

  static Test <caret>getDelegate(Test test) {
    return new Test() {
      int i;

      @Override
      void run() {
        System.out.println(test.i);
        test.run();
      }
    };
  }
}