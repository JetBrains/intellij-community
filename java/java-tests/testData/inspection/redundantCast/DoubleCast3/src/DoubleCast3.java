class Test{
  static f(){
    Object o;
    String s = (String) (String) o;

    String s2 = (String) (String) s;
  }
}
