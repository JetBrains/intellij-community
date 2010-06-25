// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      if (this instanceof Runnable) {
          Runnable runnable = (Runnable) this;
          <caret>
      }
  }
}
