// "Create inner class 'Generic'" "true-preview"
class Test {
  Test() {
    this (new Generic<String> ());
  }

  Test(String s){}

    private static class Generic<T> {
    }
}