// "Replace with '.of()'" "true-preview"

class A{
  void test(String s){
    assert s != null;
    java.util.Optional.of(<caret>s);
  }
}