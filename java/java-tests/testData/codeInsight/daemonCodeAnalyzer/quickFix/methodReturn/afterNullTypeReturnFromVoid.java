// "Make 'foo' return 'java.lang.Object'" "true"

class Test {

  <caret><selection>Object</selection> foo() {
    return null;
  }
}