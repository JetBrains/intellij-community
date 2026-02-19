// "Create inner class 'Generic'" "true-preview"
class Test {
  void foo () {
    new Generic<String> ();
  }

    private class Generic<T> {
    }
}