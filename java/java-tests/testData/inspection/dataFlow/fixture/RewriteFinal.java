final class MyClass {
  static final int[] arr = {1,2,3};
  
  void test() {
    <error descr="Cannot assign a value to final variable 'arr'">arr</error> = new int[] {4};
  }
}