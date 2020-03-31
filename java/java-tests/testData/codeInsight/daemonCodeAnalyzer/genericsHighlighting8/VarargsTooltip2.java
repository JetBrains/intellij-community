class MyTest {
  void processStrings(String s, String... list) {
    System.out.println(list);
  }

  void test() {
    processStrings("", <error descr="'processStrings(java.lang.String, java.lang.String...)' in 'MyTest' cannot be applied to '(java.lang.String, int, int)'">1</error>, <error descr="'processStrings(java.lang.String, java.lang.String...)' in 'MyTest' cannot be applied to '(java.lang.String, int, int)'">1</error>);
  }
}