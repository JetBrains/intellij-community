interface Bar {
  char c;
  char d;
}

class Foo {
  {
    String s = "".substring("".lastIndexOf(Bar.<caret>));
  }
}