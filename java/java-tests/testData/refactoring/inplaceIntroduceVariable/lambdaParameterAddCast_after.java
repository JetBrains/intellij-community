public class lambdaParameterAddCast {
  void consume(String str) {}

  void test() {
    Object r = foo -> {
        String foo1 = (String) foo;
        consume(foo1);
      System.out.println(foo1.blahblah());
    };
  }
}