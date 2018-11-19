class Test{
  static void foo(){
    Object o = null;
    boolean res = ((<warning descr="Casting 'o' to 'String' is redundant">String</warning>)o).equals(null);
  }
}
