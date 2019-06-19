// "Change return type for method 'foo'" "true"

class Test {

  void foo() {
    return <caret>null;
  }
}