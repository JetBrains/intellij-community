interface I {
  String apply(String s);
}
class Test {
  {
    I f = <selection>s</selection> -> s;
  }
}