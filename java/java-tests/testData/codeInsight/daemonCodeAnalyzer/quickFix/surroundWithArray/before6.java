// "Surround with array initialization" "true-preview"
class A {
  A(String[] s){}

  void foo(String s){
    new A(<caret>s);
  }
}