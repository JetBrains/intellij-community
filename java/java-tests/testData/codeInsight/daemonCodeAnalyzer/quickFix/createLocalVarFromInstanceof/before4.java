// "Insert '(Runnable)this' declaration" "true-preview"
class C {
  void f() {
      while (<caret>this instanceof Runnable
  }
}
