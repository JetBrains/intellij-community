// "Convert to atomic" "true-preview"

class X {

  final String[][] <caret>field = foo();

  String[][] foo() {
    return null;
  }
}