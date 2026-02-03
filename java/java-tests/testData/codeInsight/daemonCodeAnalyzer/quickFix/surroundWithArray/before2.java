// "Surround with array initialization" "false"
class A {
  void bar(int arg){
  }

  void foo(String s){
    bar(<caret>s);
  }
}