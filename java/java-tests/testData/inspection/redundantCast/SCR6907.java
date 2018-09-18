
class Test {
  void foo(String msg){}
  void foo(Object o){}

  void method(){
    foo((<warning descr="Casting 'null' to 'String' is redundant">String</warning>)null);
  }
}
