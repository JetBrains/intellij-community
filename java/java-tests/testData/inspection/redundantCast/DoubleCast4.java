class Test{
  static void f(){
    Object o = null;
    Object o2 = (<warning descr="Casting '(String)o' to 'Object' is redundant">Object</warning>) (<warning descr="Casting 'o' to 'String' is redundant">String</warning>) o;
  }
}
