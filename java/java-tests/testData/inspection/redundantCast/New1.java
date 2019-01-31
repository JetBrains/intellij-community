class Test{
  Test(Object o){}

  static void f(){
    new Test((<warning descr="Casting 'null' to 'String' is redundant">String</warning>)null);
  }
}
