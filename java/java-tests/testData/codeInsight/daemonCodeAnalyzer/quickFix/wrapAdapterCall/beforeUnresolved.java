// "Wrap parameter using 'new File()'" "false"
class Foo {
  private static Y[] parse(Iterable<String> ss) {
    return Y.toArray(X.from(ss).transform(s -> pa<caret>rse(s)));
  }
}