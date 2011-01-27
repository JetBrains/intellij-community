class Foo {
  {
    Zzoo.Impl l = new Zzoo<caret>
  }
}

interface Zzoo {
  void run();

  class Impl implements Zzoo {}
}