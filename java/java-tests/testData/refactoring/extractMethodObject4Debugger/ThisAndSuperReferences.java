public class Sample extends Base {
  public static void main(String[] args) {
    new Sample().foo();
  }

  void foo() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
    list.for<caret>Each(i -> {
      new Runnable() {
        int xxx = 0;
        @Override
        public void run() {
          this.xxx = 5; // this stays the same
        }
      }.run();
      this.a++;  // have to be qualified
      super.foo();  // have to be qualified
      a++;
      foo();
    });
    System.out.println(a);
  }
}

class Base {
  void foo() {
  }
}
