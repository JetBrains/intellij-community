// "Insert '(Runnable)this' declaration" "false"
class C {
  void f() {
      if (this instanceof Runnable<caret>) {
        Object o = (Runnable)this;
      }
  }
}
