public class lambdaParameterAddCast {
  void consume(String str) {}

  void test() {
    Object r = foo -> {
      consume(<caret>foo);
      System.out.println(foo.blahblah());
    };
  }
}