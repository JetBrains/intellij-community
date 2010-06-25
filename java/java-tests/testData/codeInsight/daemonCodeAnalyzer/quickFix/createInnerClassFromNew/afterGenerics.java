// "Create Inner Class 'Generic'" "true"
class Test {
  void foo () {
    new Generic<String> ();
  }
<caret>
    private class Generic<T> {
    }
}