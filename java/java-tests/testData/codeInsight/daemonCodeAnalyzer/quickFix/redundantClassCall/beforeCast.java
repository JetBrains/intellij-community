// "Replace with '(Integer)'" "true"
class X {
  void test(Object obj) {
    if(Integer.class.isInstance(obj)) {
      System.out.println(Integer.class.c<caret>ast(obj));
    }
  }
}