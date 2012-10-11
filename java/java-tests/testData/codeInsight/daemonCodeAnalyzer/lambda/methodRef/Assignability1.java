class Test {
  void foo(boolean flag) {
    Runnable r = null;
    Runnable x1 = flag ? System.out::println : System.out::println;
    Runnable x2 = flag ? r : System.out::println;
    Runnable x3 = flag ? System.out::println : r;
    Runnable x4 = flag ? System.out::println : new Runnable() {
      @Override
      public void run() {
      }
    };
    Runnable x5 = flag ? System.out::println : () -> {
    };
    Runnable x6 = flag ? () -> {} : System.out::println;
    Runnable x7 = flag ? () -> {} : () -> {};
    Runnable x8 = flag ? new Runnable() {
      @Override
      public void run() {
      }
    } : () -> {};
    Runnable x9 = flag ? () -> {} : r;
  }
}