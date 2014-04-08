interface SAM {
  default void <caret>foo(){}
  void bar();
}

class Test {
  {
    bar(() -> {});
  }
  
  void bar(SAM sam){}
}