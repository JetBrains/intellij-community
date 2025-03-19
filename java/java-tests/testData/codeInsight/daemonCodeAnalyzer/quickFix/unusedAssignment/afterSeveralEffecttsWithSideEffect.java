// "Remove redundant initializer" "true-preview"
class A {
  void test() {
      if (!isA() || !isB()) {
          isC();
      }
      boolean b;
    b = 10;
    System.out.println(b);
  }
  
  native boolean isA();
  native boolean isB();
  native boolean isC();
}