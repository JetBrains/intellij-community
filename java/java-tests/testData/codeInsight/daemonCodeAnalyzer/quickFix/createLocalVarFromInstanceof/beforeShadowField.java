// "Insert '(String)x' declaration" "true"

class C {
  Object x = new Object();

  void x() {
    if (x insta<caret>nceof String) {

    }
  }
}