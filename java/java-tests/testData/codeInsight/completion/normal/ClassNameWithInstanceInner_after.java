class Foo {
  {
    Zzoo l = new Zzoo()<caret>
  }
}

class Zzoo {
  void run();

  class Impl implements Zzoo {}
}