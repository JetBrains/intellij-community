// "Insert '(Runnable)this' declaration" "true-preview"
class C {
  void f() {
      while (!(this instanceof Runnable)) {
          //return;
      }
      Runnable runnable = (Runnable) this;
      <caret>
  }
}

