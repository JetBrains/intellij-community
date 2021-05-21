@interface TestAnn {
  String str() default "";
  int digit() default 2;
}

@TestAnn(digit = 1)
class Test2 {
  @TestAnn(digit = 2)
  public static void main(String[] args) {
  }
}