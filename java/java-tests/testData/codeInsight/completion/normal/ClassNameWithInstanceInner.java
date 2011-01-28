class Foo {
  {
    Zzoo l = new Zz<caret>
  }
}

class Zzoo {
  void run();

  class Impl implements Zzoo {}
}