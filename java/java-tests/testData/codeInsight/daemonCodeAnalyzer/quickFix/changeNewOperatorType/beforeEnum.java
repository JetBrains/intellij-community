// "Change 'new String()' to 'new E()'" "false"

class X {

  enum E {A, B}

  E x() {
    return new <caret>String();
  }
}
