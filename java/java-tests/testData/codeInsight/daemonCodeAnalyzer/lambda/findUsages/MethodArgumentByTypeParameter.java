interface SA<caret>M {
  void foo();
}

class Test {
  {
    SAM sam = bar(() -> {});
  }

  <T> T bar(T t){ return t;}
}