// "Make 'm()' return 'java.lang.Integer' or predecessor" "true-preview"

class Test {

  Integer m(boolean b) {
    if (b) return null;
    return 42;
  }

}