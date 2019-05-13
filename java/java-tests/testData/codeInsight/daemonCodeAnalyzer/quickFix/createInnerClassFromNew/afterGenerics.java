// "Create inner class 'Generic'" "true"
class Test {
  void foo () {
    new Generic<String> ();
  }

    private class Generic<T> {
    }
}