package package1;


import static package1.Utils1.method2;
import static package1.Utils2.method2;
import static package1.Utils2.method1;
import static package1.Utils2.*;

class Sample2 {
  public void method() {
    method2();
    method1("");
    method2("");
    method3("");
  }
}

class Utils1 {
  public static void method1(Integer parameter1, Boolean parameter2) {}
  public static void method2() {}
}
class Utils2 {
  public static void method1(String parameter) {}
  public static void method2(String parameter) {}
  public static void method3(String parameter) {}
}