
class Test {
  void test(Object obj) {
    try {
      System.out.println((<error descr="Cannot resolve symbol 'MyUnresolvedClass'">MyUnresolvedClass</error>)obj);
      System.out.println("hello");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}