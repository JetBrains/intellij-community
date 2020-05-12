package com;

class Test {
  static void f(String s, Object o){}

  void foo(){
    Object o = null;
    f((String)o, (<warning descr="Casting 'o' to 'String' is redundant">String</warning>)o);
  }
}

class Test1 {
  public Test1(int width, int height) {
    this((<warning descr="Casting 'width' to 'double' is redundant">double</warning>)width, (double)height);
  }

  private Test1(double width, double height) { }

  private String foo(Object[] array, int index1, int index2) {
    return (<warning descr="Casting 'array[index1]' to 'String' is redundant">String</warning>) array[index1] + (String) array[index2];
  }

  private static void triple(Object obj) {
    String s = (String) (<warning descr="Casting '(String)obj' to 'CharSequence' is redundant">CharSequence</warning>) (<warning descr="Casting 'obj' to 'String' is redundant">String</warning>) obj;
  }

  private Integer parenthesis(Object[] array, int index) {
    return (Integer) ((<warning descr="Casting 'array[index]' to 'Integer' is redundant">Integer</warning>) array[index]);
  }

  private String binary(Object[] array, int index1, int index2) {
    return (<warning descr="Casting 'array[index1]' to 'String' is redundant">String</warning>) array[index1] + (String) array[index2];
  }
}