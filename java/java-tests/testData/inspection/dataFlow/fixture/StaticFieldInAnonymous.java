public class StaticFieldInAnonymous {
  void test() {
    Runnable r = new Runnable() {
      private static final String s;

      static {
        s = "foo";
      }

      @Override
      public void run() {
        if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
      }
    };
  }
}