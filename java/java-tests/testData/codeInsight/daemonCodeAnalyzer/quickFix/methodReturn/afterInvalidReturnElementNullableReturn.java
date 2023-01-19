// "Make 'm()' return 'java.lang.Integer' or ancestor" "true-preview"

class Test {

  Integer m(boolean b) {
    if (b) return null;
    return 42;
  }

}