class MyTest {
  void processStrings(String... list) {
    System.out.println(list);
  }

  void test() {
    processStrings("", <error descr="'processStrings(java.lang.String...)' in 'MyTest' cannot be applied to '(java.lang.String, int, java.lang.String, java.lang.String)'">1</error>, "str", "s");
  }
}