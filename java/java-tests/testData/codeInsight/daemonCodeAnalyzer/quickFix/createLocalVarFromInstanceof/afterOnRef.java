// "Insert '(Runnable)o' declaration" "true-preview"
class C {
  void f(Object o) {
    if (o instanceof Runnable) {
        Runnable runnable = (Runnable) o;
        <caret>
    }
  }
}

