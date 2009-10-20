class Foo {
  int abcde;

  {
    Foo a;
    a.abcde = abcde;<caret>
  }
}
