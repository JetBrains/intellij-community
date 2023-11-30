class Test {
  final String X;

  {
    X = new <caret>StringBuilder().append("foo").append(1).append("bar").toString();
  }
}