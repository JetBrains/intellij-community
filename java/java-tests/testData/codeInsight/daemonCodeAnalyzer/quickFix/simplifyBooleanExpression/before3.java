// "Simplify boolean expression" "true-preview"
class X {
  void f() {
    int i = !(!false && true) || <caret>(true ? true & true : !!false | false) || this == null ? 0 : 1;
  }
}