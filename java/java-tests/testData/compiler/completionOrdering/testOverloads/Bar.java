class Bar {

  void m(Foo foo) {
    foo.asd();

    foo.asd(null, null, null);
    foo.asd(null, null, null);

    <caret>
  }

}