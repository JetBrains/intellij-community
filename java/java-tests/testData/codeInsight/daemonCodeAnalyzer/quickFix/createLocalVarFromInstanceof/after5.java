// "Insert '(Runnable)this' declaration" "true-preview"
class C {
  void f() {
      if (this instanceof Runnable) {
          Runnable runnable = (Runnable) this;
          <caret>
      }
  }
}
