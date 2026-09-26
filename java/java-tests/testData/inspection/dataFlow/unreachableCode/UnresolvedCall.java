
class Test {
  void test(Object obj) {
    try {
      <error descr="Cannot resolve method 'unresolved' in 'Test'">unresolved</error>(obj);
      System.out.println("hello");
    } catch (Ex e) {
      throw new RuntimeException(e);
    }
  }
  
  class Ex extends Exception {}
}