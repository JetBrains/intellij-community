// "Insert '(Runnable)o' declaration" "false"
class C {
  void f(Object o) {
    if (!(o instanceof Runnable)) {
      o<caret>
    }
  }
}

