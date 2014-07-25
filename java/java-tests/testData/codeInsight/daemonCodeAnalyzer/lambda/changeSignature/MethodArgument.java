interface SAM {
  void <caret>foo();
}

class Test {
  {
    bar(() -> {});
  }
  
  void bar(SAM sam){}
}