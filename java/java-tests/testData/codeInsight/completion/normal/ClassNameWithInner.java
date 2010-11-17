class Foo {
  {
    Zzoo l = new Zz<caret>
  }
}

interface Zzoo {
  void run();

  class Impl implements Zzoo {}
}