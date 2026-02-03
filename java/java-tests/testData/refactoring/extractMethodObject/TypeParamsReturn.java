class Test {
  <T> T fo<caret>o(){return null;}
  void bar(){
    String s = foo();
  }
}