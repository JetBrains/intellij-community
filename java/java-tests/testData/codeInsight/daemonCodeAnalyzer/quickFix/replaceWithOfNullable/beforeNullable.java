// "Replace with '.ofNullable()'" "true-preview"
class A{
  void test(){
    java.util.Optional.of(nu<caret>ll);
  }
}