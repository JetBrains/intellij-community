public class Foo {
  void test(int x) {
    new Inner(x).test();
  }

    private static class Inner {
        int data;

        public Inner(int x) {
            data = x * 2;
        }

        void test() {
          System.out.println(data);
        }
    }
}