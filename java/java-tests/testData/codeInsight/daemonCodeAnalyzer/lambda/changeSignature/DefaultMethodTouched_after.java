interface SAM {
  default void foo(boolean b){}
  void bar();
}

class Test {
  {
    bar(() -> {});
  }
  
  void bar(SAM sam){}
}