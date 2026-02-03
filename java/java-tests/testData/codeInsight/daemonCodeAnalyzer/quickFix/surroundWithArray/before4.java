// "Surround with array initialization" "false"
class A {
  void bar(int i, String[] args){
  }

  void foo(String s){
    bar(<caret>1, s);
  }
}