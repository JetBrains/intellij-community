// "Surround with array initialization" "true"
class A {
  A(String[] s){}

  void foo(String s){
    new A(new String[]{s});
  }
}