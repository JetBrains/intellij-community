// "Cast to 'Runnable'" "true"
class C {
  void f(Object o) {
    if (o instanceof Runnable) {
      o<caret>
    }
  }
}

