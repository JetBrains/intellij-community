// "Make 'm()' return 'double'" "true-preview"
class Test {

  <caret>m(boolean b) {
    if (b) return 42;
    return 10.0;
  }

}