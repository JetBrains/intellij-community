// "Surround with array initialization" "true-preview"
class A {
  void bar(String[] args){
  }

  void foo(String s){
    bar(new String[]{s});
  }
}