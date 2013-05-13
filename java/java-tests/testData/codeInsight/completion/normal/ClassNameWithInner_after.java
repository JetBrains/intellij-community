class Foo {
  {
    Zzoo l = new Zzoo() {
        @Override
        public void run() {
            <caret>
        }
    }
  }
}

interface Zzoo {
  void run();

  class Impl implements Zzoo {}
}