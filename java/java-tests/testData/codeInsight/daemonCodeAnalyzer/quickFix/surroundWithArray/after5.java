// "Surround with array initialization" "true"
class A {
  void bar(int i, String[] args){
  }

  void bar(int i, int j){}

  void foo(String s){
    bar(1, new String[]{s});
  }
}