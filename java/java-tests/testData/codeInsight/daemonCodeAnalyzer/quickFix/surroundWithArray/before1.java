// "Surround with array initialization" "true"
class A {
  void bar(String[] args){
  }

  void foo(String s){
    bar(<caret>s);
  }
}