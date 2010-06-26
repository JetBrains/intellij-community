// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      while (this instanceof Runnable) {
          Runnable runnable = (Runnable) this;
          <caret>
      }
  }
}
