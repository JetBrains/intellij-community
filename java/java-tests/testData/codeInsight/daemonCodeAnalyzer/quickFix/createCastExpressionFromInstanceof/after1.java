// "Cast to 'Runnable'" "true-preview"
class C {
  void f() {
    if (this instanceof Runnable) {
        ((Runnable) this)<caret>
    }
  }
}

