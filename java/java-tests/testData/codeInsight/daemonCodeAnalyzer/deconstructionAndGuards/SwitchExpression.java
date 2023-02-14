record Sample(int x, int y){}

public class Test {

  void test(Object o, Object o2){
    var out  = switch (o) {
      case String s2 when s2.length() < 1-> s2.toLowerCase();
      case Sample(int x, int w) s3 when w > 1 && s3.x() < 0 && x > 1 -> "two" + w;
      case Sample(int x, int w) s4 when s4.x() > 0 -> "two" + s4.x() + (w - x);
      case Sample(int x, int w) s4 when o2 instanceof String s -> s;
      default -> throw new IllegalStateException("Unexpected value: " + <error descr="Cannot resolve symbol 's2'">s2</error>);
    };

  }
}
