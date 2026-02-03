class Foo {
  {
    Zzoo.Impl l = new Zzoo.Impl()<caret>
  }
}

interface Zzoo {
  void run();

  class Impl implements Zzoo {}
}