// "Make 'm()' return 'java.lang.Integer' or ancestor" "true-preview"

class Test {

  void m(boolean b) {
    if (b) return n<caret>ull;
    return 42;
  }

}