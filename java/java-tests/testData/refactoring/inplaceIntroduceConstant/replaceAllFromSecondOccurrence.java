import java.lang.String;

class A {
  A(String str) {
  }
  
  public static String getVeryLongString() {
    return "";
  }
  
  void foo() {
    A a = new A(getVeryLongString());
    A a1 = new A(getVery<caret>LongString());
  }
}