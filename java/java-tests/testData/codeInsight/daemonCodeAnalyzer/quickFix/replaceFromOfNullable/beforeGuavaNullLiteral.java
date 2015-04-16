// "Replace with '.absent()'" "true"
class A{
  void test(){
    com.google.common.base.Optional.fromNullable(n<caret>ull);
  }
}