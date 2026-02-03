interface SAM {
  void foo(boolean b);
}

class Test {
  {
    bar((boolean b) -> {});
  }
  
  void bar(SAM sam){}
}