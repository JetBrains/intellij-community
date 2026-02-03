class Foo {
  java.io.File f;

  {
    f = n<caret>
  }

  java.io.File noo() {}
  Object noo2() {}

}