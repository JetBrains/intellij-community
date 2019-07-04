// "Make 'm' return 'java.lang.Integer' or predecessor" "true"

class Test {

  void <caret>m(boolean b) {
    if (b) return null;
    return 42;
  }

}