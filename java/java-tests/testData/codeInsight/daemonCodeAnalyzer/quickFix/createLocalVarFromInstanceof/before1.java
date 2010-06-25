// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
    if (this <caret>instanceof Runnable)
  }
}

