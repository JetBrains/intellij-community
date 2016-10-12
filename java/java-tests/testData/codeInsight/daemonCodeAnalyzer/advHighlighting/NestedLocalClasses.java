
class Program {
  public static void main(String[] args) {
    int[] arr = {1,2,3};
    final int x = arr.length;
    {
      class C {
        class D {
          int bar() {
            return x;
          }
        }
      }

      new Runnable() {
        class D {
          int bar() {
            return x;
          }
        }

        public void run() {}
      }.run();
    }
  }
}