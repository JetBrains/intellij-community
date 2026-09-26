import java.util.List;

class Y {
  void test(List<String> list) {
    Runnable r = () -> list.forEach(e -> {
      class X {
        final String s;
        
        X() {
          s = "foo";
        }
        
        void test() {
          if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
        }
      }
    });
  }
}