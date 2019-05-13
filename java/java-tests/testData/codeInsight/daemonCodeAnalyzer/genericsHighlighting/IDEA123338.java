class Aaa<Ta> {
  class Inner {}
  void doSmth(final Inner inner) {}
}

class Bbb<T> extends Aaa<T> {
  class SubInner extends Aaa<T>.Inner {}
  void doSmth(final SubInner inner) {}
  void ambiguousCall() {
    doSmth (new SubInner());
  }
}