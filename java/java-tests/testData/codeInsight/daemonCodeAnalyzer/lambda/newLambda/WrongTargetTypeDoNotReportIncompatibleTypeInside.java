class Test {
  void test() {
    Object obj = <error descr="Target type of a lambda conversion must be an interface">x -> {
      String s = x;
      x = "hello";
    }</error>;
  }
  
  void test2() {
    Object obj = <error descr="Target type of a lambda conversion must be an interface">x -> consume(x)</error>;
    
    Object obj1 = <error descr="Target type of a lambda conversion must be an interface">x -> consume2(x, 1)</error>;
    
    Object obj2 = x -> consume2<error descr="'consume2(java.lang.String, int)' in 'Test' cannot be applied to '(<lambda parameter>, java.lang.String)'">(x, "hello")</error>;
    
    Object obj3 = x -> consume3(x, <error descr="'consume3(java.lang.String, int, int)' in 'Test' cannot be applied to '(<lambda parameter>, java.lang.String, int)'">"hello"</error>, 1);
    
    Object obj4 = x -> consume<error descr="Expected 1 argument but found 2">(x, x)</error>;
  }
  
  void consume(String s) {}
  
  void consume2(String s, int i) {}
  
  void consume3(String s, int i, int y) {}
}