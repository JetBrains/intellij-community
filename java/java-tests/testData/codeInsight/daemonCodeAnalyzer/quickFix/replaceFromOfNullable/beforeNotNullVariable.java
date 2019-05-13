// "Replace with '.of()'" "true"

class A{
  void test(String s){
    assert s != null;
    java.util.Optional.ofNullable(<caret>s);
  }
}