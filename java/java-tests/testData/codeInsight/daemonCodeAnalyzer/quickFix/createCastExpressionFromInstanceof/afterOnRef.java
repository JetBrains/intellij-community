// "Cast to 'Runnable'" "true-preview"
class C {
  void f(Object o) {
    if (o instanceof Runnable) {
        ((Runnable) o)<caret>
    }
  }
}

