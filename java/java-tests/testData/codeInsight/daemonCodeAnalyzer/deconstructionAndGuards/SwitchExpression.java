record Sample(int x, int y){}

public class Test {

  void test(Object o, Object o2){
    var out  = switch (o) {
      case String s2 when s2.length() < 1-> s2.toLowerCase();
      case Sample(int x, int w) when w > 1 && x < 0 && x > 1 -> "two" + w;
      case Sample(int x, int w) when x > 0 -> "two" + (w - x);
      case Sample(int x, int w) when o2 instanceof String s -> s;
      default -> throw new IllegalStateException("Unexpected value: " + <error descr="Cannot resolve symbol 's2'">s2</error>);
    };

  }
}
