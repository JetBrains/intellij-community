// "Surround with array initialization" "true"
class A {

  void foo(){
    sort(<caret>"String", "String");
  }

  <T extends String, Q> void sort(T[] a, Q q){}
}