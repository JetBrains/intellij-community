class Test{
  static void f(){
    Object o = null;
    String s = (String) (<warning descr="Casting 'o' to 'String' is redundant">String</warning>) o;

    String s2 = (<warning descr="Casting '(String)s' to 'String' is redundant">String</warning>) (<warning descr="Casting 's' to 'String' is redundant">String</warning>) s;
  }
}
