import java.util.function.Supplier;

class Test {
  void bar() {
    foo(new Supplier<String>() {
        public String get() {
            String s = "";
            System.out.println(s);
            return s;
        }
    });
  }

  void foo(Supplier<String> anObject) {

      String s = anObject.get();

      System.out.println(s);
  }
}