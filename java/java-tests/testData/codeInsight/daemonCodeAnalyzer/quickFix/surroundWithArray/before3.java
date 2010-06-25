// "Surround with array initialization" "true"
class A {
  void bar(int i, String[] args){
  }

  void foo(String s){
    bar(1, <caret>s);
  }
}