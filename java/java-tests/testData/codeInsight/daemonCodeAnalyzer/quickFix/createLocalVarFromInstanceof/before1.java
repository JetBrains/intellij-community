// "Insert '(Runnable)this' declaration" "true-preview"
class C {
  void f() {
    if (this <caret>instanceof Runnable)
  }
}

