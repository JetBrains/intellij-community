// "Replace with '.ofNullable()'" "false"
class A{
  void test(){
    java.util.Optional.of(1<caret>1);
  }
}