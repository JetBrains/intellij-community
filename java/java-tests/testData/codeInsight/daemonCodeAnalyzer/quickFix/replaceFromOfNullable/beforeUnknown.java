// "Replace with '.of()'" "false"
class A{
  void test(String s){
    java.util.Optional.ofNullable(<caret>s);
  }
}