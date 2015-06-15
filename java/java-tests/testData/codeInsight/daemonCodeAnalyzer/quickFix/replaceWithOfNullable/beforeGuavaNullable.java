// "Replace with '.fromNullable()'" "true"
class A{
  void test(){
    com.google.common.base.Optional.of(n<caret>ull);
  }
}