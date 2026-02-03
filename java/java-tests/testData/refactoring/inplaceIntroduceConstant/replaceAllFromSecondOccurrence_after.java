import java.lang.String;

class A {

    public static final String O = getVeryLongString();

    A(String str) {
  }
  
  public static String getVeryLongString() {
    return "";
  }
  
  void foo() {
    A a = new A(O);
    A a1 = new A(O);
  }
}