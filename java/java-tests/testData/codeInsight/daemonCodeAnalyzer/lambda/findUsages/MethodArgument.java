interface SA<caret>M {
  void foo();
}

class Test {
  {
    bar(() -> {});
  }

  void bar(SAM sam){}
}