class Test {
  String myFoo;
  static {
      String f<caret>oo = "";
      System.out.println(myFoo);
  }
}