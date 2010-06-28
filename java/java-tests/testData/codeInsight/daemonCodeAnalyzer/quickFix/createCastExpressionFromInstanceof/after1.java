// "Cast to 'Runnable'" "true"
class C {
  void f() {
    if (this instanceof Runnable) {
        ((Runnable) this)<caret>
    }
  }
}

