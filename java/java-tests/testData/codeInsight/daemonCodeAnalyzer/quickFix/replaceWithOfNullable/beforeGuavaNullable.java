// "Replace with '.fromNullable()'" "true-preview"
class A{
  void test(){
    com.google.common.base.Optional.of(n<caret>ull);
  }
}