// "Replace '!equals()' with '!='" "true-preview"
class Test {
  void foo(Object o) {
    if(!o.e<caret>quals(null)) {

    }
  }
}