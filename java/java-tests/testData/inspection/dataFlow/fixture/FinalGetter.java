class Some {
  final boolean getFoo() { return equals(2); }
  void changeEverything() {}

  void bar() {
    if (getFoo()) {
      changeEverything();
      if (getFoo()) {

      }
    }
  }

}