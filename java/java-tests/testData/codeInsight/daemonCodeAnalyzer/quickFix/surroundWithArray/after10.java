// "Surround with array initialization" "true"
class A {

  void foo(){
    sort(new String[]{"String"}, "String");
  }

  <T extends String, Q> void sort(T[] a, Q q){}
}