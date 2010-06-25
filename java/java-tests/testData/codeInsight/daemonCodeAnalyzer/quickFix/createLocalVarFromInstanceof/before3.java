// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      while (!(<caret>this instanceof Runnable)) {
          //return;
      }
  }
}

