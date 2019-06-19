// "Change return type for method 'foo'" "true"

class Test {

  <caret><selection>void</selection> foo() {
    return null;
  }
}