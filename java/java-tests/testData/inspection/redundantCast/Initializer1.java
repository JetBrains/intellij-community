class Test{
  static void f(){
    Object o = (<warning descr="Casting 'null' to 'String' is redundant">String</warning>)null;
  }
}
