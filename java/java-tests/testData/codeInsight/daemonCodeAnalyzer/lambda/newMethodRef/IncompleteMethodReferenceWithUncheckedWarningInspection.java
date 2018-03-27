class Test {
  {
    asList(<error descr="Target type of a lambda conversion must be an interface">o -> {}</error>, 1, 2, 3);
    asList(<error descr="Integer is not a functional interface">Test::foo</error>, 1, 2, 3);
  }
  
  void foo() {}
  
  <T> void asList(T... tS) {} 
}