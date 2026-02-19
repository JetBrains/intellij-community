// "Insert '(Runnable)this' declaration" "true-preview"
class C {
  void f() {
      if (!(this instanceof <caret>Runnable)) {
          return;
      }
  }
}

