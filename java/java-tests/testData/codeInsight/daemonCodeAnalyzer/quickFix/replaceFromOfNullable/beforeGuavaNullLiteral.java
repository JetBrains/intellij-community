// "Replace with '.absent()'" "true-preview"
class A{
  void test(){
    com.google.common.base.Optional.fromNullable(n<caret>ull);
  }
}