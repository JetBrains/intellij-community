// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      if (this instanceof Runnable) {   <caret>
      }
  }
}
