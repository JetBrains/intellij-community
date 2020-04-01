class Test {
  void foo(Object o) {
    try {
      Runnable r = () -> {
          newMethod(o);
      };
    } catch (Throwable e) {}
  }

    private void newMethod(Object o) {
        System.out.println(o);
    }
}