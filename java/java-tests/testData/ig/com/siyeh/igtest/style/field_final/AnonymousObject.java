import java.util.Optional;

class Test {
  public static void main(String[] args) {
    var o = new Object() {
      int a = 0;
    };
    
    o.a = 1;
  }
  
  void test() {
    Optional.of("foo").map(x -> {
      var o = new Object() {
        private String s = x;
      };
      return o;
    }).ifPresent(o -> o.s = "foo");
  }
}