// "Surround with array initialization" "false"
class A {

  void foo(){
    sort(<caret>"String", "String");
  }

  <T extends Integer, Q> void sort(T[] a, Q q){}
}