class Foo {
  {
    zoo(o -> o.<caret>)
  }

  void zoo(NonLambda arg, int a) {}
  void zoo(NonLambda arg, int a, int b) {}
}
class NonLambda {}