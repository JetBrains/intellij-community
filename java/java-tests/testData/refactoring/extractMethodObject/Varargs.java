class Test {
  <T> void fo<caret>o(T... t){
      for (T t1 : t) {} 
  }
  void bar(){
    foo("");
  }
}