// "Insert '(Runnable)o' declaration" "true"
class C {
  void f(Object o) {
    if (o instanceof Runnable) {
        Runnable o1 = (Runnable) o;
        <caret>
    }
  }
}

