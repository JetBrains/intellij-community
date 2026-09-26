// "Remove redundant initializer" "true-preview"
class A {
  void test() {
    boolean b = isA() <caret>&& isB() || isC();
    b = 10;
    System.out.println(b);
  }
  
  native boolean isA();
  native boolean isB();
  native boolean isC();
}