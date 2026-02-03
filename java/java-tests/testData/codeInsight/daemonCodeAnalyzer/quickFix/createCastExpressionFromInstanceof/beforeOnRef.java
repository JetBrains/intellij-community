// "Cast to 'Runnable'" "true-preview"
class C {
  void f(Object o) {
    if (o instanceof Runnable) {
      o<caret>
    }
  }
}

