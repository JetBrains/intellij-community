// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      if (!(this instanceof <caret>Runnable)) {
          return;
      }
  }
}

