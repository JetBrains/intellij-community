// "Create block" "true"
class X {
  void foo(int i) {
    switch(i) {
      case 1 -><caret>
      case 2 ->
    }
  }
}